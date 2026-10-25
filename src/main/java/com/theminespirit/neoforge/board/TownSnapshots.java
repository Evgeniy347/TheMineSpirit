package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.SettlementPlot;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageSites;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownPlanner;
import com.theminespirit.core.town.TownSnapshot;
import com.theminespirit.neoforge.movement.PathNetworkService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

public final class TownSnapshots {
    private TownSnapshots() {}

    public static Optional<TownSnapshot> current(ServerLevel level) {
        SettlementPlotStore store = SettlementPlotStore.get(level);
        Optional<TownPlan> town = store.town();
        Optional<StageSite> site = store.site();
        Optional<SettlementPlot> plot = store.plot();
        if (town.isEmpty() || site.isEmpty() || plot.isEmpty()) {
            return Optional.empty();
        }
        BoardService service = BoardRegistry.get(level);
        StageSite stage = site.orElseThrow();
        String wood = plot.orElseThrow().wood();
        Map<String, TownSnapshot.Progress> progress = new LinkedHashMap<>();
        progress.put(StageSites.HUT, step(level, service, BoardCase.BUILD_HOUSE, stage, wood));
        progress.put(
                StageSites.YARD,
                sum(
                        step(level, service, BoardCase.STOCK_CHESTS, stage, wood),
                        step(level, service, BoardCase.CRAFT_FURNACE, stage, wood)));
        progress.put(StageSites.MINE, step(level, service, BoardCase.BUILD_MINE, stage, wood));
        progress.put(StageSites.GROVE, step(level, service, BoardCase.PLANT_GROVE, stage, wood));
        progress.put(TownPlanner.FENCE, step(level, service, BoardCase.BUILD_FENCE, stage, wood));
        progress.put(TownPlanner.TORCHES, step(level, service, BoardCase.LIGHTING, stage, wood));
        List<List<BlockCoord>> paths = PathNetworkService.segments(level);
        return Optional.of(TownSnapshot.of(town.orElseThrow(), stage, progress, paths));
    }

    private static TownSnapshot.Progress step(
            ServerLevel level, BoardService service, BoardCase step, StageSite site, String wood) {
        return FirstStepsRunner.progress(level, service, step, site, wood);
    }

    private static TownSnapshot.Progress sum(TownSnapshot.Progress first, TownSnapshot.Progress second) {
        return new TownSnapshot.Progress(
                first.done() + second.done(), first.total() + second.total(), first.damaged() || second.damaged());
    }
}
