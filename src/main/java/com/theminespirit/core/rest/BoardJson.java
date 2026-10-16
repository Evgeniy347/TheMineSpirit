package com.theminespirit.core.rest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON-представление заданий доски для GET/POST/DELETE /v1/board: имена полей — контракт v1. */
public final class BoardJson {
    private BoardJson() {
    }

    public static String toJson(BoardTask task) {
        return RestJson.write(toMap(task));
    }

    public static String listJson(List<BoardTask> tasks) {
        List<Object> items = new ArrayList<>(tasks.size());
        for (BoardTask task : tasks) {
            items.add(toMap(task));
        }
        return RestJson.write(Map.of("tasks", items));
    }

    public static Map<String, Object> toMap(BoardTask task) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", task.id());
        map.put("case", task.boardCase());
        map.put("priority", task.priority());
        map.put("assignee", task.assignee());
        map.put("status", task.status());
        map.put("requiredItems", task.requiredItems());
        map.put("missing", task.missing());
        map.put("dependsOn", task.dependsOn());
        map.put("createdAt", task.createdAt());
        map.put("level", task.level());
        map.put("cause", task.cause());
        return map;
    }
}
