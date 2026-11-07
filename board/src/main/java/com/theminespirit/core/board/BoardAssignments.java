package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

final class BoardAssignments {
    private final Map<String, BoardTicket> tickets;
    private final Map<String, BoardStatus> statuses;
    private final Map<String, UUID> claims;
    private final LongSupplier now;

    BoardAssignments(
            Map<String, BoardTicket> tickets,
            Map<String, BoardStatus> statuses,
            Map<String, UUID> claims,
            LongSupplier now) {
        this.tickets = Objects.requireNonNull(tickets, "tickets не может быть null: держатели читаются с доски");
        this.statuses = Objects.requireNonNull(statuses, "statuses не может быть null: выдано только ASSIGNED");
        this.claims = Objects.requireNonNull(claims, "claims не может быть null: держатель задания — дух");
        this.now = Objects.requireNonNull(now, "now не может быть null: порядок держателей идёт по тикам");
    }

    List<BoardTicket> assignedTo(UUID spirit) {
        Objects.requireNonNull(spirit, "spirit не может быть null: держатель задания — дух");
        List<BoardTicket> held = new ArrayList<>();
        for (BoardTicket ticket : tickets.values()) {
            if (statuses.get(ticket.id()) == BoardStatus.ASSIGNED && holds(ticket, spirit)) {
                held.add(ticket);
            }
        }
        held.sort(QueueOrder.of(tickets.values(), statuses, now.getAsLong()));
        return List.copyOf(held);
    }

    Map<UUID, Integer> counts() {
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        for (BoardTicket ticket : tickets.values()) {
            if (statuses.get(ticket.id()) != BoardStatus.ASSIGNED) {
                continue;
            }
            UUID holder = holder(ticket);
            if (holder != null) {
                counts.merge(holder, 1, Integer::sum);
            }
            for (UUID executor : ticket.executors()) {
                if (!executor.equals(holder)) {
                    counts.merge(executor, 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    boolean updatePending(BoardTicket ticket) {
        Objects.requireNonNull(ticket, "Билет не может быть null: обновлять нечего");
        if (statuses.get(ticket.id()) != BoardStatus.PENDING) {
            return false;
        }
        tickets.put(ticket.id(), ticket);
        return true;
    }

    Optional<BoardCase> occupation(UUID spirit) {
        for (BoardTicket ticket : assignedTo(spirit)) {
            if (BoardActivities.isActivity(ticket.boardCase())) {
                return Optional.of(ticket.boardCase());
            }
        }
        return Optional.empty();
    }

    private boolean holds(BoardTicket ticket, UUID spirit) {
        return ticket.inCrew(spirit) || spirit.equals(claims.get(ticket.id()));
    }

    private @Nullable UUID holder(BoardTicket ticket) {
        return claims.containsKey(ticket.id())
                ? claims.get(ticket.id())
                : ticket.assignee().orElse(null);
    }
}
