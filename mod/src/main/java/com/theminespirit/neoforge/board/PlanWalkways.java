package com.theminespirit.neoforge.board;

import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.Walkways;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;

public final class PlanWalkways {
    private static final int CACHE_TICKS = 600;
    private static final Map<ServerLevel, Cached> LAST = new WeakHashMap<>();
    private static final Object LOCK = new Object();

    private record Cached(long bucket, Set<ColumnMask.Cell> cells) {}

    private PlanWalkways() {}

    public static boolean on(ServerLevel level, int x, int z) {
        synchronized (LOCK) {
            long bucket = level.getGameTime() / CACHE_TICKS;
            Cached before = LAST.get(level);
            if (before == null || before.bucket() != bucket) {
                Optional<TownPlan> town = SettlementPlotStore.get(level).town();
                Set<ColumnMask.Cell> cells = town.isEmpty()
                        ? Set.of()
                        : new HashSet<>(Walkways.of(
                                town.orElseThrow(), SettlementPlotStore.currentStage(BoardRegistry.get(level))));
                before = new Cached(bucket, cells);
                LAST.put(level, before);
            }
            return before.cells().contains(new ColumnMask.Cell(x, z));
        }
    }
}
