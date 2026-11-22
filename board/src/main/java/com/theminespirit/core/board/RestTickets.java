package com.theminespirit.core.board;

import java.util.Objects;

public final class RestTickets {
    public static final String PREFIX = "rest:";

    private RestTickets() {}

    public static String idFor(String taskId) {
        Objects.requireNonNull(taskId, "taskId не может быть null: тикет REST-задачи называют по id задачи");
        return PREFIX + taskId;
    }

    public static boolean is(String ticketId) {
        return ticketId != null && ticketId.startsWith(PREFIX);
    }
}
