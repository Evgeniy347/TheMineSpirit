package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BuildGap;
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
        return of(level, step, 1, stage, fallback);
    }

    static FirstStepsRunner.Placed of(
            ServerLevel level, BoardCase step, int buildingLevel, StageSite stage, String fallback) {
        long bucket = level.getGameTime() / CACHE_TICKS;
        if (bucket != cacheBucket) {
            cacheBucket = bucket;
            CACHE.clear();
        }
        String key = level.dimension().location() + "|" + step.name() + "|" + buildingLevel + "|" + stage.anchor() + "|"
                + fallback;
        return CACHE.computeIfAbsent(key, ignored -> compute(level, step, buildingLevel, stage, fallback, key));
    }

    private static FirstStepsRunner.Placed compute(
            ServerLevel level, BoardCase step, int buildingLevel, StageSite stage, String fallback, String key) {
        BuildGap.Gap gap = FirstStepsRunner.gap(level, step, buildingLevel, stage, fallback);
        AuditJournal.watch(level, step, key, gap);
        return new FirstStepsRunner.Placed(gap.done(), gap.total(), gap.unknown());
    }
}
