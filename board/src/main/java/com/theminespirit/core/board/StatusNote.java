package com.theminespirit.core.board;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class StatusNote {
    private StatusNote() {}

    public static String line(
            BoardLog.Event event,
            String ticketId,
            BoardCase boardCase,
            int attempts,
            @Nullable String waitReason,
            @Nullable String chosen) {
        return line(event, ticketId, boardCase, attempts, waitReason, chosen, null);
    }

    public static String line(
            BoardLog.Event event,
            String ticketId,
            BoardCase boardCase,
            int attempts,
            @Nullable String waitReason,
            @Nullable String chosen,
            @Nullable String details) {
        Objects.requireNonNull(event, "Событие перехода не может быть null: строка называет переход");
        Objects.requireNonNull(ticketId, "id задания не может быть null: строка называет задание");
        Objects.requireNonNull(boardCase, "Кейс задания не может быть null: строка называет кейс");
        if (attempts < 0) {
            throw new IllegalArgumentException(
                    "Попыток " + attempts + " меньше нуля: счётчик провалов не бывает отрицательным");
        }
        return "[board] " + event.name() + " задание=" + ticketId + " кейс=" + boardCase + " попыток=" + attempts
                + " waitReason=" + dash(waitReason) + " выбор=" + dash(chosen) + " подробности=" + dash(details);
    }

    private static String dash(@Nullable String text) {
        return text == null || text.isBlank() ? "—" : text;
    }
}
