package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardLedger;
import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.TownPlan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
        town.ifPresent(plan -> {
            Map<String, List<String>> groups = new HashMap<>();
            Map<String, Boolean> done = new HashMap<>();
            for (var place : plan.placements()) {
                Optional<String> graphId = StageCatalog.graphId(place.id());
                if (graphId.isEmpty() || graph.buildings().get(graphId.orElseThrow()) == null) {
                    continue;
                }
                boolean built = ledger.hasPlan(place.id(), 1)
                        || StageCatalog.entry(place.id())
                                .flatMap(entry -> worldBuilt(level, scan, plan, entry))
                                .orElse(false);
                if (built) {
                    ledger.notePlan(place.id(), 1);
                }
                groups.computeIfAbsent(graphId.orElseThrow(), key -> new ArrayList<>())
                        .add(place.id());
                done.put(place.id(), built);
            }
            putGroups(groups, done, buildings);
        });
    }

    static void putGroups(Map<String, List<String>> groups, Map<String, Boolean> done, Map<String, Built> buildings) {
        groups.forEach((graphId, ids) -> {
            boolean all = ids.stream().allMatch(id -> done.getOrDefault(id, false));
            buildings.put(graphId, all ? Built.BUILT : Built.PLANNED);
        });
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
