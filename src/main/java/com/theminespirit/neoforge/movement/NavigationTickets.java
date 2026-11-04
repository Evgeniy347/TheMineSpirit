package com.theminespirit.neoforge.movement;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.navigation.NavigationChunkRequests;
import com.theminespirit.core.navigation.NavigationChunkRequests.Chunk;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

public final class NavigationTickets {
    // mc: уровень тикета = 33 - расстояние; для загрузки без тика сущностей хватает 2.
    private static final int TICKET_DISTANCE = 2;
    private static final int RADIUS_CHUNKS = 1;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TicketType<ChunkPos> NAV_TICKET =
            TicketType.create("theminespirit_nav", Comparator.comparingLong(ChunkPos::toLong));
    private static final Map<UUID, Holding> HELD = new HashMap<>();
    private static NavigationChunkRequests budget;

    private record Holding(ServerLevel level, Set<ChunkPos> held) {}

    private NavigationTickets() {}

    public static boolean prepare(SpiritEntity spirit, BlockCoord target, int radius) {
        if (!(spirit.level() instanceof ServerLevel level)) {
            return false;
        }
        Holding holding = HELD.computeIfAbsent(spirit.getUUID(), ignored -> new Holding(level, new HashSet<>()));
        BlockCoord from = new BlockCoord(
                spirit.blockPosition().getX(),
                spirit.blockPosition().getY(),
                spirit.blockPosition().getZ());
        List<Chunk> missing = new ArrayList<>();
        for (Chunk chunk : NavigationChunkRequests.corridor(from, target, Math.max(RADIUS_CHUNKS, radius))) {
            if (!level.isLoaded(new ChunkPos(chunk.x(), chunk.z()).getWorldPosition())) {
                missing.add(chunk);
            }
        }
        if (missing.isEmpty()) {
            return false;
        }
        boolean canHoldMore = holding.held().size() < SpiritConfig.navChunkBudget();
        List<Chunk> take =
                budget().next(level.getGameTime(), missing, holding.held().size());
        for (Chunk chunk : take) {
            ChunkPos pos = new ChunkPos(chunk.x(), chunk.z());
            holding.held().add(pos);
            level.getChunkSource().addRegionTicket(NAV_TICKET, pos, TICKET_DISTANCE, pos, true);
        }
        if (!take.isEmpty()) {
            RouteDetour.forget(spirit);
            LOGGER.info(
                    "[route] дух {}: загрузка чанков на пути к {} — запрошено {}, держим {} из {}",
                    spirit.displayName(),
                    target,
                    take.size(),
                    holding.held().size(),
                    SpiritConfig.navChunkBudget());
        }
        return canHoldMore;
    }

    public static boolean loading(SpiritEntity spirit) {
        return HELD.containsKey(spirit.getUUID());
    }

    public static int held(SpiritEntity spirit) {
        Holding holding = HELD.get(spirit.getUUID());
        return holding == null ? 0 : holding.held().size();
    }

    public static void release(SpiritEntity spirit) {
        Holding holding = HELD.remove(spirit.getUUID());
        if (holding == null) {
            return;
        }
        for (ChunkPos pos : holding.held()) {
            holding.level().getChunkSource().removeRegionTicket(NAV_TICKET, pos, TICKET_DISTANCE, pos, true);
        }
    }

    private static NavigationChunkRequests budget() {
        if (budget == null) {
            budget = new NavigationChunkRequests(SpiritConfig.navChunkBudget(), SpiritConfig.navChunkPerSecond());
        }
        return budget;
    }
}
