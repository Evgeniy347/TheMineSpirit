package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.CaseMaterials;
import com.theminespirit.core.board.ResourceGate;
import com.theminespirit.core.board.StockNeed;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.OrchardPlot;
import com.theminespirit.core.town.OrchardSites;
import com.theminespirit.core.town.StageSite;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ExtraOrchardPost {
    private ExtraOrchardPost() {}

    static boolean handles(String building) {
        return OrchardSites.isExtra(building);
    }

    static boolean post(GraphPost post, GraphTickets.Ticket ticket, GraphPlan.Task task) {
        String building = ticket.node();
        StageSite site = post.scan.stage();
        Optional<OrchardPlot> plot = site.extraOrchard(building);
        if (plot.isEmpty()) {
            return false;
        }
        if (post.done(ticket.id()) || post.open(ticket.id())) {
            return true;
        }
        OrchardPlot known = plot.orElseThrow();
        BlockCoord origin = known.origin(site.anchor().y());
        TicketTarget target = TicketTarget.at(origin)
                .withWork(StageBuilds.of(post.level, BoardCase.BUILD_ORCHARD, site, 0))
                .withWood(known.wood())
                .withBuilding(building);
        Map<String, Integer> need = new HashMap<>(CaseMaterials.need(
                TicketPlans.plan(BoardCase.BUILD_ORCHARD, 1, target), FirstStepsRunner.materialSource(post.level)));
        FirstStepsRunner.minusPlaced(
                post.level, TicketPlans.plan(BoardCase.BUILD_ORCHARD, 1, target), known.wood(), need);
        if (!ResourceGate.missing(new StockNeed(need), post.stock).isEmpty()) {
            return true;
        }
        post.submit(
                new BoardTicket(
                        ticket.id(),
                        BoardCase.BUILD_ORCHARD,
                        Optional.empty(),
                        task.priority(),
                        post.now,
                        1,
                        ticket.id(),
                        List.of(),
                        Map.copyOf(need)),
                target);
        return true;
    }
}
