package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardConfig;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphNames;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphTickets;
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

    boolean station(GraphTickets.Ticket ticket, GraphPlan.Task task) {
        post.submit(
                post.ticket(
                        ticket.id(),
                        BoardCase.CRAFTING_STATION,
                        Optional.empty(),
                        task.priority(),
                        ticket.id(),
                        post.items(task)),
                TicketTarget.at(post.craftPoint()).withSupply("crafting_table", 1));
        return true;
    }
}
