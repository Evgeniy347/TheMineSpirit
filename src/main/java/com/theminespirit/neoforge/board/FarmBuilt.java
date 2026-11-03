package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLedger;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.town.OrchardSites;
import com.theminespirit.core.town.TownPlan;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

final class FarmBuilt {
    private FarmBuilt() {}

    static void apply(
            BoardService service,
            ServerLevel level,
            FirstStepsRunner.StageScan scan,
            BoardLedger ledger,
            SupplyGraph graph,
            Optional<TownPlan> town,
            Map<String, Built> buildings) {
        Map<BoardCase, Integer> sharing = new EnumMap<>(BoardCase.class);
        graph.buildings().values().forEach(building -> sharing.merge(building.boardCase(), 1, Integer::sum));
        for (SupplyGraph.Building building : graph.buildings().values()) {
            if (buildings.containsKey(building.id())) {
                continue;
            }
            boolean ownCase = sharing.get(building.boardCase()) == 1;
            if (done(service, building.id())
                    || (ownCase && ledger.has(building.boardCase(), 1))
                    || (building.boardCase() == BoardCase.BUILD_PEN && ledger.has(BoardCase.BUILD_PEN, 1))) {
                buildings.put(building.id(), Built.BUILT);
            } else if (town.flatMap(plan -> OrchardSites.placement(plan, building.id()))
                    .isPresent()) {
                buildings.put(building.id(), orchardDone(service, building) ? Built.BUILT : Built.PLANNED);
            }
        }
        if (buildings.getOrDefault("smoker", Built.ABSENT) != Built.BUILT
                && FishingSite.smokerNear(level, scan.home())) {
            buildings.put("smoker", Built.BUILT);
        }
        if (buildings.getOrDefault("apiary", Built.ABSENT) != Built.BUILT && HiveSite.hiveNear(level, scan.home())) {
            buildings.put("apiary", Built.BUILT);
        }
    }

    private static boolean done(BoardService service, String building) {
        return service.board()
                .statusOf(GraphTickets.PREFIX + "building:" + building)
                .filter(BoardStatus.DONE::equals)
                .isPresent();
    }

    private static boolean orchardDone(BoardService service, SupplyGraph.Building building) {
        return building.boardCase() == BoardCase.BUILD_ORCHARD
                && service.board()
                        .statusOf(GraphTickets.PREFIX + "building:" + building.id())
                        .filter(BoardStatus.DONE::equals)
                        .isPresent();
    }
}
