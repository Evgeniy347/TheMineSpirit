package com.theminespirit.core.board;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class PlotEditWho {
    private PlotEditWho() {}

    public static String culprit(List<String> tickets, @Nullable String script) {
        Objects.requireNonNull(tickets, "Список заданий виновника не может быть null: пустой — пустым списком");
        StringBuilder text = new StringBuilder();
        if (!tickets.isEmpty()) {
            text.append("задание ").append(String.join(", ", tickets));
        }
        if (script != null && !script.isBlank()) {
            if (!text.isEmpty()) {
                text.append("; ");
            }
            text.append("действие ").append(script);
        }
        return text.isEmpty() ? "" : ", " + text;
    }
}
