package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLedger;
import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.WarehouseRules;
import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.TownPlan;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

final class PlanStatesInputs {
    private PlanStatesInputs() {}

    static void apply(
            ServerLevel level,
            FirstStepsRunner.StageScan scan,
            Optional<TownPlan> town,
            BoardLedger ledger,
            SupplyGraph graph,
            Map<String, Built> buildings) {
        town.ifPresent(plan -> plan.placements()
                .forEach(place -> StageCatalog.graphId(place.id()).ifPresent(id -> {
                    SupplyGraph.Building building = graph.buildings().get(id);
                    if (building == null || buildings.containsKey(id)) {
                        return;
                    }
                    boolean built = ledger.hasPlan(place.id(), WarehouseRules.levelOf(building.id()))
                            || StageCatalog.entry(place.id())
                                    .flatMap(entry -> worldBuilt(level, scan, plan, entry))
                                    .orElse(false);
                    if (built) {
                        ledger.notePlan(place.id(), WarehouseRules.levelOf(building.id()));
                    }
                    buildings.put(id, built ? Built.BUILT : Built.PLANNED);
                })));
    }

    static Optional<Boolean> worldBuilt(
            ServerLevel level, FirstStepsRunner.StageScan scan, TownPlan town, StageCatalog.Entry entry) {
        Optional<BlockCoord> origin =
                StageCatalog.origin(entry.id(), town, scan.stage().anchor().y());
        if (origin.isEmpty()) {
            return Optional.of(false);
        }
        TicketTarget target =
                TicketTarget.at(origin.orElseThrow()).withScheme(entry.scheme()).withBuilding(entry.id());
        FirstStepsRunner.Placed placed =
                FirstStepsRunner.placedBlocks(level, TicketPlans.plan(BoardCase.BUILD_PLACED, 1, target), scan.wood());
        if (!placed.confident()) {
            return Optional.empty();
        }
        return Optional.of(placed.total() > 0 && placed.done() == placed.total());
    }
}
