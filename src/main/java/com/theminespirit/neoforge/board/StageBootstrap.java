package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.SettlementPlot;
import com.theminespirit.core.town.StageSite;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;

final class StageBootstrap {
    private static final Set<ServerLevel> READY = ConcurrentHashMap.newKeySet();

    private StageBootstrap() {}

    static void apply(ServerLevel level, BoardService service) {
        if (READY.contains(level)) {
            return;
        }
        SettlementPlotStore store = SettlementPlotStore.get(level);
        Optional<StageSite> site = store.site(1);
        Optional<SettlementPlot> plot = store.plot();
        if (site.isEmpty() || plot.isEmpty()) {
            return;
        }
        READY.add(level);
        StageWorldGate.apply(
                level, service, site.orElseThrow(), plot.orElseThrow().wood());
    }

    static void forget(ServerLevel level) {
        READY.remove(level);
    }
}
