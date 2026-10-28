package com.theminespirit.core.rest;

import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardLogEntry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BoardJson {
    private BoardJson() {}

    public static String logJson(List<BoardLogEntry> entries) {
        List<Object> lines = new ArrayList<>(entries.size());
        for (BoardLogEntry entry : entries) {
            lines.add(BoardLog.toMap(entry));
        }
        return RestJson.write(Map.of("entries", lines));
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
        map.put("effectivePriority", task.effectivePriority());
        map.put("modifiers", task.modifiers());
        map.put("done", task.done());
        map.put("startedAt", task.startedAt());
        map.put("finishedAt", task.finishedAt());
        map.put("durationTicks", task.durationTicks());
        map.put("waitReason", task.waitReason());
        map.put("supplyItem", task.supplyItem());
        map.put("supplyCount", task.supplyCount());
        return map;
    }
}
