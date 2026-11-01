package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.town.StageSite;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;

final class BuiltAuditCache {
    private static final long CACHE_TICKS = 600;
    private static long cacheBucket = Long.MIN_VALUE;
    private static final Map<String, FirstStepsRunner.Placed> CACHE = new HashMap<>();

    private BuiltAuditCache() {}

    static FirstStepsRunner.Placed of(ServerLevel level, BoardCase step, StageSite stage, String fallback) {
        long bucket = level.getGameTime() / CACHE_TICKS;
        if (bucket != cacheBucket) {
            cacheBucket = bucket;
            CACHE.clear();
        }
        String key = level.dimension().location() + "|" + step.name() + "|" + stage.anchor() + "|" + fallback;
        return CACHE.computeIfAbsent(key, ignored -> compute(level, step, stage, fallback));
    }

    private static FirstStepsRunner.Placed compute(
            ServerLevel level, BoardCase step, StageSite stage, String fallback) {
        String wood = BuildingWoodChoice.stored(level, step, stage, fallback);
        return FirstStepsRunner.placedBlocks(
                level, TicketPlans.plan(step, 1, FirstStepsRunner.targetFor(level, step, stage, wood)), wood);
    }
}
