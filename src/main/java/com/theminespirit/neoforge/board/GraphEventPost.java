package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.neoforge.terminal.CraftOrders;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class GraphEventPost {
    private static final String EAT = "eat";
    private static final String ORDER = "craft_player_order";
    private static final Set<String> DEMOLITION = Set.of("demolish_stray", "clear_floating");
    private static final Set<String> COLLECT = Set.of("collect_drops", "collect_saplings");

    private GraphEventPost() {}

    static boolean post(GraphPost post, GraphTickets.Ticket ticket) {
        if (GraphMorePost.handles(ticket)) {
            return GraphMorePost.post(post, ticket, ticket.task());
        }
        if (life(ticket)) {
            return true;
        }
        if (FarmPost.event(post, ticket)) {
            return true;
        }
        if (StorePost.event(post, ticket)) {
            return true;
        }
        if (GearPost.event(post, ticket)) {
            return true;
        }
        if (EAT.equals(ticket.node())) {
            return hungry(post, ticket);
        }
        if (BoardCase.REPAIR_TOOL.name().toLowerCase(Locale.ROOT).equals(ticket.node())) {
            return RepairWatch.post(
                    post.level, post.service, post.spirits, ticket.task().priority(), post.now);
        }
        if (ORDER.equals(ticket.node())) {
            CraftOrders.refresh(post.level);
            return true;
        }
        if (DEMOLITION.contains(ticket.node())) {
            Optional<TownPlan> town = SettlementPlotStore.get(post.level).town();
            town.ifPresent(
                    plan -> PlanDemolition.tick(post.level, post.service, post.scan.stage(), plan, post.now, post));
            return town.isPresent();
        }
        if (YardFloorWatch.handles(ticket.node())) {
            return YardFloorWatch.post(post.level, post.service, post.scan.stage(), post.now);
        }
        if ("fill_crater".equals(ticket.node())) {
            return new GraphHarvestPost(post).craters(ticket);
        }
        if (ticket.task().boardCase() == BoardCase.REPAIR_FENCE) {
            return new GraphHarvestPost(post).fences(ticket);
        }
        if (WorldPost.handles(ticket.node())) {
            return WorldPost.post(post, ticket);
        }
        return MorePost.event(post, ticket) || (COLLECT.contains(ticket.node()) && collect(post, ticket));
    }

    private static boolean life(GraphTickets.Ticket ticket) {
        return ticket.id().startsWith(GraphTickets.PREFIX + "event:life_");
    }

    private static boolean hungry(GraphPost post, GraphTickets.Ticket ticket) {
        GraphFood.post(
                post.level,
                post.service,
                post.spirits,
                post.stock,
                ticket.id(),
                ticket.task().priority(),
                post.now);
        FarmPost.eat(post, ticket);
        return true;
    }

    private static boolean collect(GraphPost post, GraphTickets.Ticket ticket) {
        boolean saplings = "collect_saplings".equals(ticket.node());
        Optional<BlockCoord> at = saplings ? post.around.decay() : post.around.anyDrop();
        if (at.isEmpty()) {
            return false;
        }
        Optional<BlockCoord> target = saplings ? GraphTargets.leafDropStand(post.level, at.orElseThrow()) : at;
        if (target.isEmpty()) {
            return false;
        }
        post.submit(
                post.ticket(
                        ticket.id(),
                        BoardCase.COLLECT_DROPS,
                        Optional.empty(),
                        ticket.task().priority(),
                        ticket.id(),
                        Map.of()),
                TicketTarget.at(target.orElseThrow()));
        return true;
    }
}
