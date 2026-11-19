package com.theminespirit.neoforge;

import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

final class StandClaims {
    private static final int TTL = 400;
    private static final Map<ServerLevel, Map<UUID, Claim>> CLAIMS = new WeakHashMap<>();

    private StandClaims() {}

    static Set<BlockCoord> taken(ServerLevel level, Entity spirit, BlockCoord around) {
        Set<BlockCoord> cells = new HashSet<>();
        Map<UUID, Claim> claims = CLAIMS.computeIfAbsent(level, ignored -> new HashMap<>());
        long now = level.getGameTime();
        claims.entrySet().removeIf(entry -> entry.getValue().until() < now);
        for (Map.Entry<UUID, Claim> entry : claims.entrySet()) {
            if (entry.getKey().equals(spirit.getUUID())) {
                continue;
            }
            BlockCoord cell = entry.getValue().cell();
            if (Math.abs(cell.x() - around.x()) <= BoardTuning.workStandRadius() + 1
                    && Math.abs(cell.z() - around.z()) <= BoardTuning.workStandRadius() + 1
                    && Math.abs(cell.y() - around.y()) <= BoardTuning.workStandRadius()) {
                cells.add(cell);
            }
        }
        return cells;
    }

    static void claim(Entity spirit, BlockCoord cell) {
        if (!(spirit.level() instanceof ServerLevel level)) {
            return;
        }
        CLAIMS.computeIfAbsent(level, ignored -> new HashMap<>())
                .put(spirit.getUUID(), new Claim(cell, level.getGameTime() + TTL));
    }

    static void release(Entity spirit) {
        if (!(spirit.level() instanceof ServerLevel level)) {
            return;
        }
        Map<UUID, Claim> claims = CLAIMS.get(level);
        if (claims != null) {
            claims.remove(spirit.getUUID());
        }
    }

    private record Claim(BlockCoord cell, long until) {}
}
