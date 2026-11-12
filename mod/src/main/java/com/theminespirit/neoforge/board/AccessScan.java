package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.Walkways;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class AccessScan {
    static final String EVENT = "access_blocked";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CACHE_TICKS = 600;
    private static final List<String> KEEP = List.of(
            "chest",
            "barrel",
            "crafting_table",
            "furnace",
            "smoker",
            "_door",
            "_bed",
            "anvil",
            "fence",
            "_gate",
            "torch",
            "lantern",
            "ladder");
    private static final Predicate<String> KEPT = id -> KEEP.stream().anyMatch(id::contains);
    private static final Map<ServerLevel, Cached> LAST = new WeakHashMap<>();
    private static final Object LOCK = new Object();

    private record Cached(long bucket, List<BlockCoord> cut) {}

    private AccessScan() {}

    static List<BlockCoord> cut(ServerLevel level, BoardService service, StageSite site, BlockCoord home) {
        synchronized (LOCK) {
            long bucket = level.getGameTime() / CACHE_TICKS;
            Cached before = LAST.get(level);
            if (before != null && before.bucket() == bucket) {
                return before.cut();
            }
            List<BlockCoord> cut = scan(level, service, site, home);
            LAST.put(level, new Cached(bucket, cut));
            return cut;
        }
    }

    static boolean blocked(ServerLevel level, BoardService service, StageSite site, BlockCoord home) {
        return !cut(level, service, site, home).isEmpty();
    }

    private static List<BlockCoord> scan(ServerLevel level, BoardService service, StageSite site, BlockCoord home) {
        Optional<TownPlan> town = SettlementPlotStore.get(level).town();
        if (town.isEmpty()) {
            return List.of();
        }
        long started = System.nanoTime();
        List<BlockCoord> cut = Walkways.clearance(
                        WorldAreaScan.view(level, site),
                        KEPT,
                        Walkways.of(town.orElseThrow(), SettlementPlotStore.currentStage(service)),
                        town.orElseThrow().anchor().y())
                .stream()
                .sorted(Comparator.comparingInt(
                                (BlockCoord cell) -> Math.abs(cell.x() - home.x()) + Math.abs(cell.z() - home.z()))
                        .thenComparing(Comparator.comparingInt(BlockCoord::y).reversed()))
                .toList();
        if (!cut.isEmpty()) {
            LOGGER.info(
                    "[access] тропы плана: срезать {} за {} мс — проход по улицам до калиток, дверей и двора",
                    cut.size(),
                    (System.nanoTime() - started) / 1_000_000L);
        }
        return cut;
    }
}
