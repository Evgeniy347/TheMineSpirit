package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardLedger;
import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.StageRules;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.board.graph.StageBuilt;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.board.route.RouteMarker;
import com.theminespirit.core.board.route.RoutePlan;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.TownPlan;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

final class GraphBuildInputs {
    private GraphBuildInputs() {}

    static BuildingStates of(
            ServerLevel level, BoardService service, FirstStepsRunner.StageScan scan, SupplyGraph graph) {
        BoardLedger ledger = service.board().ledger();
        Map<String, Built> buildings = new HashMap<>(StageBuilt.of(scan.facts()));
        RoutePlan routes = RouteStore.get(level).plan();
        for (RouteMarker marker : routes.markers().values()) {
            buildings.put(marker.resource().pathBuilding(), marker.pathBuilt() ? Built.BUILT : Built.PLANNED);
            if (marker.routeBuilt()) {
                buildings.put(marker.resource().routeBuilding(), Built.BUILT);
            } else if (marker.pathBuilt()) {
                buildings.put(marker.resource().routeBuilding(), Built.PLANNED);
            }
        }
        Optional<TownPlan> town = SettlementPlotStore.get(level).town();
        buildings.putAll(planBuilt(level, service, scan, town));
        PlanStatesInputs.apply(level, scan, town, ledger, graph, buildings);
        FarmBuilt.apply(service, level, scan, ledger, graph, town, buildings);
        return BuildingStates.of(level, buildings);
    }

    static Built auditedState(Optional<Boolean> world) {
        return world.orElse(false) ? Built.BUILT : Built.PLANNED;
    }

    private static Map<String, Built> planBuilt(
            ServerLevel level, BoardService service, FirstStepsRunner.StageScan scan, Optional<TownPlan> town) {
        BoardLedger ledger = service.board().ledger();
        Map<String, Built> states = new HashMap<>();
        StageRules rules = DataTables.INSTANCE.stages().current();
        int stage = service.board().ledger().stage();
        if (stage > rules.stages().size()) {
            return states;
        }
        for (String id : rules.stages().get(stage - 1).plan()) {
            Optional<StageCatalog.Entry> entry = StageCatalog.entry(id);
            if (entry.isEmpty() || entry.orElseThrow().boardCase() != BoardCase.BUILD_PLACED) {
                continue;
            }
            Optional<Boolean> world = town.isPresent()
                    ? PlanStatesInputs.worldBuilt(level, scan, town.orElseThrow(), entry.orElseThrow())
                    : Optional.empty();
            Built built = auditedState(world);
            if (world.orElse(false)) {
                ledger.notePlan(id, 1);
            }
            states.put(id, built);
        }
        return states;
    }
}
