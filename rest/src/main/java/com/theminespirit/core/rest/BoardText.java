package com.theminespirit.core.rest;

import com.theminespirit.core.board.BoardStatus;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

public final class BoardText {
    private BoardText() {}

    public static String line(BoardTask task) {
        StringBuilder text = new StringBuilder();
        text.append(task.id())
                .append(" — ")
                .append(task.boardCase())
                .append(", уровень ")
                .append(task.level())
                .append(", приоритет ")
                .append(task.effectivePriority())
                .append(", ")
                .append(statusRu(task.status()));
        if (!task.modifiers().isEmpty()) {
            text.append(", модификаторы: ").append(String.join(", ", task.modifiers()));
        }
        if (task.done()) {
            text.append(", выполнено");
        }
        text.append(", владелец: ").append(task.assignee() == null ? "любой свободный" : task.assignee());
        if (!task.missing().isEmpty()) {
            text.append(", не хватает: ").append(missing(task.missing()));
        }
        String supply = supply(task);
        if (supply != null) {
            text.append(", ").append(supply);
        }
        if (!task.dependsOn().isEmpty()) {
            text.append(", после: ").append(String.join(", ", task.dependsOn()));
        }
        return text.toString();
    }

    public static @Nullable String supply(BoardTask task) {
        if (task.supplyItem() == null || task.supplyCount() <= 0) {
            return null;
        }
        String verb = switch (task.boardCase()) {
            case "GATHER_MATERIALS" -> "добыть";
            case "SUPPLY_SITE" -> "подвезти";
            default -> null;
        };
        return verb == null ? null : verb + ": " + task.supplyItem() + " ×" + task.supplyCount();
    }

    public static String missing(Map<String, Integer> missing) {
        StringJoiner parts = new StringJoiner(", ");
        for (Map.Entry<String, Integer> entry : new TreeMap<>(missing).entrySet()) {
            parts.add(entry.getKey() + " ×" + entry.getValue());
        }
        return parts.toString();
    }

    public static String statusRu(String status) {
        BoardStatus parsed;
        try {
            parsed = BoardStatus.valueOf(status);
        } catch (IllegalArgumentException unknown) {
            return status;
        }
        return switch (parsed) {
            case PENDING -> "ждёт выдачи";
            case WAITING_RESOURCES -> "ждёт ресурсов";
            case ASSIGNED -> "выдано";
            case BLOCKED -> "заблокировано";
            case DONE -> "выполнено";
            case FAILED -> "провалено";
            case CANCELLED -> "отменено";
        };
    }
}
