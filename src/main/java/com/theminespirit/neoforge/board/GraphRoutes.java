package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.GatherBatch;
import com.theminespirit.core.board.MaterialSource;
import com.theminespirit.core.board.PlotLeveling;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.board.route.RouteResource;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

final class GraphRoutes {
    private final GraphPost post;
    private final ServerLevel level;
    private final BoardService service;
    private final FirstStepsRunner.StageScan scan;
    private final SupplyGraph graph;
    private final MaterialSource source;
    private final int helpers;

    GraphRoutes(
            GraphPost post,
            ServerLevel level,
            BoardService service,
            FirstStepsRunner.StageScan scan,
            SupplyGraph graph,
            MaterialSource source,
            int helpers) {
        this.post = post;
        this.level = level;
        this.service = service;
        this.scan = scan;
        this.graph = graph;
        this.source = source;
        this.helpers = helpers;
    }

    boolean gather(GraphTickets.Ticket ticket, String item, RouteResource resource) {
        Optional<BlockCoord> point = RouteStore.get(level).point(resource);
        if (point.isEmpty()) {
            return false;
        }
        BlockCoord dig = point.orElseThrow();
        PlotLeveling plan = RouteWorks.dig(level, dig);
        if (plan.cut().isEmpty()) {
            return false;
        }
        BlockCoord home = scan.stage().workYard();
        int output = source.outputCount(item);
        List<Integer> portions = GatherBatch.split(GatherBatch.round(ticket.count(), output), output, helpers);
        for (int part = 0; part < portions.size(); part++) {
            String partId = part == 0 ? ticket.id() : ticket.id() + GraphPost.PART;
            post.submit(
                    post.ticket(
                            partId,
                            ticket.task().boardCase(),
                            Optional.empty(),
                            ticket.task().priority(),
                            partId,
                            Map.of()),
                    TicketTarget.at(home)
                            .withRelocate(dig, home)
                            .withLeveling(plan)
                            .withSupply(item, portions.get(part)));
        }
        return true;
    }

    boolean building(GraphTickets.Ticket ticket) {
        Optional<RouteResource> resource = RouteResource.ofBuilding(ticket.node());
        if (resource.isEmpty()) {
            return false;
        }
        RouteResource known = resource.orElseThrow();
        RouteStore routes = RouteStore.get(level);
        Optional<BlockCoord> point = routes.point(known);
        if (point.isEmpty()) {
            return false;
        }
        BoardStatus status = service.board().statusOf(ticket.id()).orElse(null);
        if (status == BoardStatus.DONE) {
            if (known.isPath(ticket.node())) {
                routes.markPathBuilt(known);
            } else {
                routes.markRouteBuilt(known);
            }
            return true;
        }
        if (post.open(ticket.id())) {
            return true;
        }
        BlockCoord to = point.orElseThrow();
        TicketTarget target;
        if (known.isPath(ticket.node())) {
            PlotLeveling plan = RouteWorks.path(level, scan.stage().anchor(), to, pathBlock(ticket.node()));
            if (plan.fill().isEmpty()) {
                return false;
            }
            target = TicketTarget.at(to).withRelocate(scan.stage().anchor(), to).withLeveling(plan);
        } else if (known.isRoute(ticket.node())) {
            target = TicketTarget.at(to);
        } else {
            return false;
        }
        post.submit(
                post.ticket(
                        ticket.id(),
                        BoardCase.BUILD_STREETS,
                        Optional.empty(),
                        ticket.task().priority(),
                        ticket.id(),
                        Map.of()),
                target);
        return true;
    }

    private String pathBlock(String building) {
        SupplyGraph.Building known = graph.buildings().get(building);
        if (known == null || known.materials().isEmpty()) {
            return "dirt";
        }
        return known.materials().keySet().iterator().next();
    }
}
