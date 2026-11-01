package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.SettlementPlot;
import com.theminespirit.core.board.route.RouteMarker;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageSites;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownPlanner;
import com.theminespirit.core.town.TownSnapshot;
import com.theminespirit.core.ui.TownMapView;
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
        BoardService service = BoardRegistry.get(level);
        int stageValue = service.board().ledger().stage();
        Optional<StageSite> site = store.site(stageValue).or(store::site);
        Optional<SettlementPlot> plot = store.plot();
        if (town.isEmpty() || site.isEmpty() || plot.isEmpty()) {
            return Optional.empty();
        }
        StageSite stage = site.orElseThrow();
        String wood = plot.orElseThrow().wood();
        Map<String, TownSnapshot.Progress> progress = new LinkedHashMap<>();
        progress.put(StageSites.HUT, step(level, service, BoardCase.BUILD_HOUSE, stage, wood));
        progress.put(
                StageSites.YARD,
                sum(
                        sum(
                                step(level, service, BoardCase.STOCK_CHESTS, stage, wood),
                                step(level, service, BoardCase.CRAFT_FURNACE, stage, wood)),
                        YardFloorWatch.progress(level, stage)));
        progress.put(StageSites.MINE, step(level, service, BoardCase.BUILD_MINE, stage, wood));
        progress.put(StageSites.ORCHARD, step(level, service, BoardCase.BUILD_ORCHARD, stage, wood));
        progress.put(TownPlanner.FENCE, step(level, service, BoardCase.BUILD_FENCE, stage, wood));
        progress.put(TownPlanner.TORCHES, step(level, service, BoardCase.LIGHTING, stage, wood));
        List<List<BlockCoord>> paths = PathNetworkService.segments(level);
        return Optional.of(TownSnapshot.of(town.orElseThrow(), stage, stageValue, progress, paths));
    }

    public static Optional<TownMapView.Bounds> markerBounds(ServerLevel level) {
        SettlementPlotStore store = SettlementPlotStore.get(level);
        Optional<TownPlan> town = store.town();
        Optional<StageSite> site = store.site();
        if (town.isEmpty() || site.isEmpty() || store.plot().isEmpty()) {
            return Optional.empty();
        }
        TownPlan plan = town.orElseThrow();
        ColumnMask region = plan.region();
        int[] box = {
            region.minX(), region.minZ(), region.minX() + region.width() - 1, region.minZ() + region.depth() - 1
        };
        include(box, plan.fence().posts());
        include(box, site.orElseThrow().ring().posts());
        for (RouteMarker route : RouteStore.get(level).plan().markers().values()) {
            box[0] = Math.min(box[0], route.point().x());
            box[1] = Math.min(box[1], route.point().z());
            box[2] = Math.max(box[2], route.point().x());
            box[3] = Math.max(box[3], route.point().z());
        }
        return Optional.of(new TownMapView.Bounds(box[0], box[1], box[2], box[3]));
    }

    private static void include(int[] box, List<ColumnMask.Cell> cells) {
        for (ColumnMask.Cell cell : cells) {
            box[0] = Math.min(box[0], cell.x());
            box[1] = Math.min(box[1], cell.z());
            box[2] = Math.max(box[2], cell.x());
            box[3] = Math.max(box[3], cell.z());
        }
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
