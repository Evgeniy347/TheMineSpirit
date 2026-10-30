package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphNames;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphTickets;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class GraphCrafting {
    private static final int TORCHES_PER_CRAFT = 4;
    private static final int TORCH_BATCH = 16;
    private static final int CHARCOAL_LOGS = 4;
    private static final int CHARCOAL_BATCH = 2;

    private final GraphPost post;

    GraphCrafting(GraphPost post) {
        this.post = post;
    }

    boolean torches(GraphTickets.Ticket ticket, GraphPlan.Task task) {
        int coal = post.stock.available().getOrDefault("coal", 0);
        int charcoal = post.stock.available().getOrDefault("charcoal", 0);
        String fuel = coal >= charcoal ? "coal" : "charcoal";
        int torches = Math.min(ticket.count(), TORCH_BATCH);
        int crafts = Math.min((torches + TORCHES_PER_CRAFT - 1) / TORCHES_PER_CRAFT, Math.max(coal, charcoal));
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
                TicketTarget.at(post.craftPoint()).withCount(crafts * TORCHES_PER_CRAFT));
        return true;
    }

    boolean charcoal(GraphTickets.Ticket ticket, GraphPlan.Task task) {
        String log = GraphNames.item(post.graph, "#logs", post.scan.wood());
        Optional<UUID> holder = post.scan.furnaceBlock() ? Optional.empty() : GraphCrew.furnaceHolder(post.spirits);
        post.submit(
                post.ticket(
                        ticket.id(),
                        task.boardCase(),
                        holder,
                        task.priority(),
                        ticket.id(),
                        Map.of(log, CHARCOAL_LOGS)),
                TicketTarget.at(post.craftPoint())
                        .withSupply(log, CHARCOAL_LOGS)
                        .withCount(CHARCOAL_BATCH));
        return true;
    }
}
