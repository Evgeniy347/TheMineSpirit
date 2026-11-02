package com.theminespirit.core.board;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class BoardTransitions {
    private static final Set<BoardLog.Event> LOGGED =
            Set.of(BoardLog.Event.DONE, BoardLog.Event.REOPENED, BoardLog.Event.CANCELLED);
    private static final String REASON_PREFIX = "reason=";
    private static final String UNKNOWN_REASON = "не записана";

    private BoardTransitions() {}

    public static Optional<String> line(
            BoardLog.Event event, BoardTicket ticket, int attempts, @Nullable String holder) {
        Objects.requireNonNull(event, "Событие перехода не может быть null: строку пишут по переходу");
        Objects.requireNonNull(ticket, "Задание перехода не может быть null: строка называет его id и кейс");
        if (!LOGGED.contains(event)) {
            return Optional.empty();
        }
        return Optional.of(StatusNote.line(
                event, ticket.id(), ticket.boardCase(), attempts, null, holder, details(event, ticket)));
    }

    private static String details(BoardLog.Event event, BoardTicket ticket) {
        return switch (event) {
            case FAILED -> "причина=" + failureReason(ticket);
            case DONE -> "причина=выполнено: " + ticket.cause();
            case REOPENED -> "причина=отметка снята: " + ticket.cause();
            case CANCELLED -> "причина=снято: " + ticket.cause();
            default -> "";
        };
    }

    private static String failureReason(BoardTicket ticket) {
        return ticket.modifiers().stream()
                .filter(modifier -> modifier.startsWith(REASON_PREFIX))
                .map(modifier -> modifier.substring(REASON_PREFIX.length()))
                .findFirst()
                .orElse(UNKNOWN_REASON);
    }
}
