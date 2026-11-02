package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public final class ToolLossRelease {
    public record Released(String ticketId, BoardCase boardCase, String reason) {
        public Released {
            Objects.requireNonNull(ticketId, "Id снятого задания не может быть null: по нему гасится задача духа");
            Objects.requireNonNull(boardCase, "Кейс снятого задания не может быть null: он пишется в журнал");
            Objects.requireNonNull(reason, "Причина снятия не может быть null: она пишется в журнал доски");
        }
    }

    private ToolLossRelease() {}

    public static List<Released> apply(Board board, UUID holder, Collection<String> items, boolean walledIn) {
        return apply(board, holder, items, walledIn, ticket -> null);
    }

    public static List<Released> apply(
            Board board,
            UUID holder,
            Collection<String> items,
            boolean walledIn,
            Function<BoardTicket, @Nullable String> supplyOf) {
        Objects.requireNonNull(board, "Доска не может быть null: задания держателя снимаются с неё");
        Objects.requireNonNull(holder, "Держатель не может быть null: снимаются задания конкретного духа");
        Objects.requireNonNull(items, "Инвентарь не может быть null: пустой — пустой набор");
        Objects.requireNonNull(supplyOf, "Поставка задания не может быть null: без неё не видно, нужен ли топор");
        List<Released> released = new ArrayList<>();
        for (BoardTicket ticket : board.assignedTo(holder)) {
            if (ToolGate.keep(ticket, items, walledIn, supplyOf.apply(ticket)) instanceof ToolGate.Release release
                    && board.release(ticket.id())) {
                released.add(new Released(ticket.id(), ticket.boardCase(), release.reason()));
            }
        }
        return List.copyOf(released);
    }
}
