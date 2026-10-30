package com.theminespirit.neoforge.respawn;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.base.RespawnCell;
import com.theminespirit.core.base.RespawnRules;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.board.SettlementPlot;
import com.theminespirit.core.incident.InventoryText;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.IncidentSnapshots;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import com.theminespirit.neoforge.inventory.SpiritInventory;
import com.theminespirit.neoforge.movement.RouteHistory;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

public final class SpiritRespawnService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long RETRY_TICKS = 20;

    private SpiritRespawnService() {}

    public static void register(IEventBus bus) {
        Objects.requireNonNull(bus, "bus не может быть null: без шины возрождение не узнает о смерти духа");
        bus.addListener((LivingDeathEvent event) -> onDeath(event));
        bus.addListener((ServerTickEvent.Post event) -> onServerTick(event));
    }

    private static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof SpiritEntity spirit)
                || spirit.level().isClientSide()) {
            return;
        }
        if (!(spirit.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos death = spirit.blockPosition();
        String cause = event.getSource().getLocalizedDeathMessage(spirit).getString();
        LOGGER.info(
                "[death] дух {}: {}, место ({}, {}, {}), инвентарь {}, последние шаги маршрута {}",
                spirit.displayName(),
                cause,
                death.getX(),
                death.getY(),
                death.getZ(),
                InventoryText.of(IncidentSnapshots.inventoryOf(spirit)),
                RouteHistory.drain(spirit));
        dropInventory(spirit);
        if (spirit.isDemo()) {
            return;
        }
        SpiritRespawnStore store = SpiritRespawnStore.get(level);
        if (store.pending(spirit.getUUID()).isPresent()) {
            return;
        }
        Optional<BlockCoord> base = baseOf(level, spirit);
        if (base.isEmpty()) {
            return;
        }
        store.add(new SpiritRespawnStore.Pending(
                spirit.getUUID(),
                spirit.displayName(),
                spirit.home(),
                base.orElseThrow(),
                new BlockCoord(death.getX(), death.getY(), death.getZ()),
                cause,
                level.getGameTime() + RespawnRules.loadDefault().delayTicks()));
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            long now = level.getGameTime();
            for (SpiritRespawnStore.Pending entry :
                    SpiritRespawnStore.get(level).due(now)) {
                attempt(level, entry, now);
            }
        }
    }

    private static void attempt(ServerLevel level, SpiritRespawnStore.Pending entry, long now) {
        if (level.getEntity(entry.id()) != null) {
            reschedule(level, entry, now);
            return;
        }
        Optional<BlockCoord> cell = RespawnCell.nearest(entry.base(), terrain(level));
        if (cell.isEmpty()) {
            reschedule(level, entry, now);
            return;
        }
        BlockCoord born = cell.orElseThrow();
        SpiritEntity spirit = ModEntities.SPIRIT.get().create(level);
        if (spirit == null) {
            LOGGER.error("[respawn] дух {} не создан: тип сущности недоступен, возрождение отложено", entry.name());
            reschedule(level, entry, now);
            return;
        }
        spirit.setUUID(entry.id());
        spirit.setCustomName(Component.literal(entry.name()));
        spirit.moveTo(born.x() + 0.5D, born.y(), born.z() + 0.5D, 0.0F, 0.0F);
        entry.home().ifPresent(spirit::setHome);
        spirit.setPersistenceRequired();
        if (!level.addFreshEntity(spirit)) {
            LOGGER.error("[respawn] дух {} не добавлен в мир: возрождение отложено", entry.name());
            reschedule(level, entry, now);
            return;
        }
        SpiritRespawnStore.get(level).remove(entry.id());
        LOGGER.info(
                "[respawn] дух {} погиб ({}, {}, {}, {}) → возрождён в ({}, {}, {})",
                entry.name(),
                entry.cause(),
                entry.death().x(),
                entry.death().y(),
                entry.death().z(),
                born.x(),
                born.y(),
                born.z());
        journal(level, entry, born, now);
    }

    private static void reschedule(ServerLevel level, SpiritRespawnStore.Pending entry, long now) {
        SpiritRespawnStore.get(level).reschedule(entry.id(), now + RETRY_TICKS);
    }

    private static Optional<BlockCoord> baseOf(ServerLevel level, SpiritEntity spirit) {
        Optional<BlockCoord> plot = SettlementPlotStore.get(level).plot().map(SettlementPlot::anchor);
        return RespawnCell.base(plot, spirit.home());
    }

    private static RespawnCell.Terrain terrain(ServerLevel level) {
        return new RespawnCell.Terrain(
                floor -> {
                    BlockPos pos = new BlockPos(floor.x(), floor.y(), floor.z());
                    return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP);
                },
                cell -> level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z()))
                        .isAir());
    }

    private static void dropInventory(SpiritEntity spirit) {
        SpiritInventory inventory = spirit.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            spirit.spawnAtLocation(stack.copy());
            inventory.setItem(slot, ItemStack.EMPTY);
        }
    }

    private static void journal(ServerLevel level, SpiritRespawnStore.Pending entry, BlockCoord born, long now) {
        if (!SpiritConfig.boardEnabled()) {
            return;
        }
        BoardRegistry.get(level)
                .journal()
                .recordEntry(BoardLog.note(
                        "respawn:" + entry.name(),
                        BoardCase.ACTIVITY_EXTERNAL,
                        now,
                        List.of("cause=" + entry.cause(), "death=" + point(entry.death()), "born=" + point(born)),
                        entry.id().toString(),
                        ConfigStamp.current()));
    }

    private static String point(BlockCoord coord) {
        return coord.x() + "," + coord.y() + "," + coord.z();
    }
}
