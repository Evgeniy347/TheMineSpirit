package com.theminespirit.neoforge.board;

import com.theminespirit.core.base.NamespacedId;
import com.theminespirit.core.board.BoardConfig;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.graph.GraphNames;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.board.graph.SupplyGraph;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class GraphCrafting {
    private final GraphPost post;

    GraphCrafting(GraphPost post) {
        this.post = post;
    }

    boolean torches(GraphTickets.Ticket ticket, GraphPlan.Task task) {
        int coal = post.stock.available().getOrDefault("coal", 0);
        int charcoal = post.stock.available().getOrDefault("charcoal", 0);
        String fuel = coal >= charcoal ? "coal" : "charcoal";
        var plans = BoardConfig.current().plans();
        int torches = Math.min(ticket.count(), plans.torchBatch());
        int crafts =
                Math.min((torches + plans.torchesPerCraft() - 1) / plans.torchesPerCraft(), Math.max(coal, charcoal));
        if (crafts <= 0) {
            return true;
        }
        post.submit(
                post.ticket(
                        ticket.id(),
                        task.boardCase(),
                        Optional.empty(),
                        task.priority(),
                        ticket.id(),
                        Map.of(fuel, crafts)),
                TicketTarget.at(post.craftPoint()).withCount(crafts * plans.torchesPerCraft()));
        return true;
    }

    boolean charcoal(GraphTickets.Ticket ticket, GraphPlan.Task task) {
        var plans = BoardConfig.current().plans();
        String log = GraphNames.item(post.graph, "#logs", post.scan.wood());
        Optional<UUID> holder = post.scan.furnaceBlock() ? Optional.empty() : GraphCrew.furnaceHolder(post.spirits);
        post.submit(
                post.ticket(
                        ticket.id(),
                        task.boardCase(),
                        holder,
                        task.priority(),
                        ticket.id(),
                        Map.of(log, plans.charcoalLogs())),
                TicketTarget.at(post.craftPoint())
                        .withSupply(log, plans.charcoalLogs())
                        .withCount(plans.charcoalBatch()));
        return true;
    }

    static boolean handles(GraphTickets.Ticket ticket, GraphPlan.Task task) {
        return task.boardCase() == BoardCase.GATHER_MATERIALS
                && (ticket.node().startsWith("craft_") || "bake_bread".equals(ticket.node()));
    }

    static String item(SupplyGraph graph, GraphPlan.Task task, String wood) {
        String item = GraphNames.item(graph, task.makes(), wood);
        if ("food".equals(item) && ("craft_food".equals(task.id()) || "bake_bread".equals(task.id()))) {
            item = "bread";
        }
        return item.indexOf(':') < 0 ? NamespacedId.MINECRAFT + item : item;
    }

    boolean post(GraphTickets.Ticket ticket, GraphPlan.Task task) {
        return task.spirit().isPresent() ? spiritCraft(ticket, task) : handles(ticket, task) && way(ticket, task);
    }

    boolean way(GraphTickets.Ticket ticket, GraphPlan.Task task) {
        String item = item(post.graph, task, post.scan.wood());
        if (!GraphItemIds.known(item)) {
            return false;
        }
        int count = Math.max(1, ticket.count());
        post.submit(
                post.ticket(
                        ticket.id(),
                        task.boardCase(),
                        Optional.empty(),
                        task.priority(),
                        ticket.id(),
                        post.items(task)),
                TicketTarget.at(post.craftPoint()).withCraft(item, count).withCount(count));
        return true;
    }

    boolean spiritCraft(GraphTickets.Ticket ticket, GraphPlan.Task task) {
        String item = GraphNames.item(post.graph, task.makes(), post.scan.wood());
        if (!GraphItemIds.known(item)) {
            return false;
        }
        UUID spirit = UUID.fromString(task.spirit().orElseThrow());
        post.submit(
                post.ticket(
                        ticket.id(),
                        task.boardCase(),
                        Optional.of(spirit),
                        task.priority(),
                        ticket.id(),
                        post.items(task)),
                TicketTarget.at(post.craftPoint()).withCraft(item, 1));
        return true;
    }
}
