package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.neoforge.terminal.CraftOrders;
import java.util.Comparator;
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
        if (More4Post.post(post, ticket)) {
            return true;
        }
        if (life(post, ticket)) {
            return true;
        }
        if ("expedition".equals(ticket.node())) {
            return GraphExpeditionPost.post(post, ticket);
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
            return RepairWatch.post(post, ticket.task().priority());
        }
        if (ORDER.equals(ticket.node())) {
            CraftOrders.refresh(post.level);
            return true;
        }
        if (ChestDemolition.EVENT.equals(ticket.node())) {
            return ChestDemolition.tick(post, ticket);
        }
        if (DEMOLITION.contains(ticket.node())) {
            Optional<TownPlan> town = SettlementPlotStore.get(post.level).town();
            town.ifPresent(plan -> PlanDemolition.tick(
                    post.level, post.service, post.scan.stage(), plan, post.now, post, ticket.id()));
            return town.isPresent();
        }
        if (YardFloorWatch.handles(ticket.node())) {
            return YardFloorWatch.post(post);
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

    private static boolean life(GraphPost post, GraphTickets.Ticket ticket) {
        if (!ticket.id().startsWith(GraphTickets.PREFIX + "event:life_")) {
            return false;
        }
        if (ticket.task().boardCase() == BoardCase.ACTIVITY_DEFEND) {
            return GraphDefensePost.post(post, ticket);
        }
        return ticket.node().contains("retreat") && retreat(post, ticket);
    }

    private static boolean retreat(GraphPost post, GraphTickets.Ticket ticket) {
        GraphFacts.Spirit weakest = post.facts.spirits().values().stream()
                .min(Comparator.comparingDouble(GraphFacts.Spirit::health))
                .orElse(null);
        if (weakest == null) {
            return false;
        }
        for (var spirit : post.spirits) {
            if (spirit.isRemoved() || !spirit.getUUID().toString().equals(weakest.id())) {
                continue;
            }
            String id = ticket.id() + ":" + spirit.getUUID();
            if (post.open(id) || post.done(id)) {
                return true;
            }
            post.submit(
                    post.ticket(
                            id,
                            BoardCase.ACTIVITY_RETREAT,
                            Optional.of(spirit.getUUID()),
                            Math.min(100, Math.max(1, ticket.task().priority())),
                            ticket.id(),
                            Map.of()),
                    TicketTarget.at(post.scan.home()).withPlan(new ScriptCall("retreat", Map.of())));
            return true;
        }
        return false;
    }

    private static boolean hungry(GraphPost post, GraphTickets.Ticket ticket) {
        GraphFood.post(post, ticket.id(), ticket.task().priority());
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
