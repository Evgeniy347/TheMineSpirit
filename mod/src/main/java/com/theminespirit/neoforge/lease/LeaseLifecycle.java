package com.theminespirit.neoforge.lease;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.lease.Lease;
import com.theminespirit.core.board.lease.LeaseSupervisor;
import com.theminespirit.neoforge.BuildReservationHub;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.coordinator.WorkReserveHub;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

public final class LeaseLifecycle {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHECK_EVERY_TICKS = 20;

    private LeaseLifecycle() {}

    public static void register(IEventBus bus) {
        Objects.requireNonNull(bus, "bus не может быть null: без шины аренды не узнают о смерти и рестарте");
        bus.addListener(LeaseLifecycle::onDeath);
        bus.addListener((ServerStartedEvent event) -> onStarted(event.getServer()));
        bus.addListener((ServerTickEvent.Post event) -> onTick(event.getServer()));
    }

    private static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof SpiritEntity spirit)
                || spirit.level().isClientSide()
                || !(spirit.level() instanceof ServerLevel level)) {
            return;
        }
        int count = LeaseData.get(level).orphanSpirit(spirit.getUUID());
        WorkReserveHub.releaseAll(spirit);
        if (count > 0) {
            LOGGER.info("[lease] дух {} умер: брошено аренд {}", spirit.getUUID(), count);
        }
    }

    private static void onStarted(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            int count = LeaseData.get(level).orphanAllActive();
            if (count > 0) {
                LOGGER.info(
                        "[lease] старт сервера: в {} брошено активных аренд {}",
                        level.dimension().location(),
                        count);
            }
        }
    }

    private static void onTick(MinecraftServer server) {
        if (server == null || server.getTickCount() % CHECK_EVERY_TICKS != 0) {
            return;
        }
        long timeout = BoardTuning.leaseSuspendTimeoutTicks();
        for (ServerLevel level : server.getAllLevels()) {
            LeaseData data = LeaseData.get(level);
            BuildReservationHub.forLevel(level).expire(level.getGameTime());
            if (data.book().active().isEmpty()) {
                continue;
            }
            List<Lease> due = LeaseSupervisor.due(
                    data.book(), spiritStates(level), data.suspendedSince(), level.getGameTime(), timeout);
            for (Lease lease : due) {
                data.book().orphan(lease.id());
                if (lease.spirit() != null) {
                    Entity owner = level.getEntity(lease.spirit());
                    if (owner instanceof SpiritEntity spirit) {
                        WorkReserveHub.releaseAll(spirit);
                    }
                }
                LOGGER.info(
                        "[lease] аренда {} ({}) брошена: владелец {} не работает над ней",
                        lease.id(),
                        lease.purpose(),
                        lease.spirit());
            }
            if (!due.isEmpty()) {
                data.changed();
            }
        }
    }

    private static Map<UUID, LeaseSupervisor.SpiritState> spiritStates(ServerLevel level) {
        BoardService service = BoardRegistry.peek(level).orElse(null);
        Map<UUID, LeaseSupervisor.SpiritState> states = new HashMap<>();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof SpiritEntity spirit) || spirit.isRemoved() || !spirit.isAlive()) {
                continue;
            }
            boolean suspended =
                    spirit.brainIfPresent().map(brain -> brain.isSuspended()).orElse(false);
            Set<String> tickets = service == null
                    ? null
                    : service.board().assignedTo(spirit.getUUID()).stream()
                            .map(BoardTicket::id)
                            .collect(Collectors.toUnmodifiableSet());
            states.put(spirit.getUUID(), new LeaseSupervisor.SpiritState(suspended, tickets));
        }
        return states;
    }
}
