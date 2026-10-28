package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphTickets.Ticket;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.PortalScripts;
import java.util.Map;
import java.util.Optional;

final class PortalPost {
    private static final String PORTAL_HALL = "portal_hall";
    private static final String FLINT_AND_STEEL = "flint_and_steel";
    private static final String WATER_BUCKET = "water_bucket";

    private PortalPost() {}

    static boolean way(GraphPost post, Ticket ticket, GraphPlan.Task task) {
        if (!PortalScripts.CAST_OBSIDIAN.equals(ticket.node())) {
            return false;
        }
        post.submit(
                post.ticket(
                        ticket.id(),
                        BoardCase.GATHER_MATERIALS,
                        Optional.empty(),
                        task.priority(),
                        ticket.id(),
                        Map.of(WATER_BUCKET, ticket.count())),
                TicketTarget.at(castPoint(post)).withSupply(task.makes(), ticket.count()));
        return true;
    }

    static boolean building(GraphPost post, Ticket ticket) {
        if (!PortalScripts.LIGHT_PORTAL.equals(ticket.node())) {
            return false;
        }
        if (post.open(ticket.id()) || post.done(ticket.id())) {
            return true;
        }
        post.submit(
                post.ticket(
                        ticket.id(),
                        BoardCase.GATHER_MATERIALS,
                        Optional.empty(),
                        ticket.task().priority(),
                        ticket.id(),
                        Map.of(FLINT_AND_STEEL, 1)),
                TicketTarget.at(portalPoint(post))
                        .withSupply(FLINT_AND_STEEL, 1)
                        .withScheme(PortalScripts.LIGHT_PORTAL));
        return true;
    }

    private static BlockCoord castPoint(GraphPost post) {
        return post.around
                .resource("lava")
                .map(lava -> GraphTargets.stand(post.level, lava))
                .orElseGet(() -> post.source(PortalScripts.CAST_OBSIDIAN));
    }

    private static BlockCoord portalPoint(GraphPost post) {
        return SettlementPlotStore.get(post.level)
                .town()
                .flatMap(town -> town.placement(PORTAL_HALL))
                .map(place -> new BlockCoord(
                        place.centerX(), post.scan.stage().anchor().y(), place.centerZ()))
                .orElse(post.scan.stage().anchor());
    }
}
