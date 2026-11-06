package com.theminespirit.core.rest;

import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardLogEntry;
import com.theminespirit.core.ui.BoardTileGroup;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BoardJson {
    private BoardJson() {}

    public static String logJson(List<BoardLogEntry> entries) {
        return logJson(entries, 0L);
    }

    public static String logJson(List<BoardLogEntry> entries, long sinceTick) {
        List<Object> lines = new ArrayList<>(entries.size());
        for (BoardLogEntry entry : entries) {
            lines.add(BoardLog.toMap(entry));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("entries", lines);
        body.put(
                "sinceTick",
                entries.stream().mapToLong(BoardLogEntry::tick).max().orElse(sinceTick));
        return RestJson.write(body);
    }

    public static String toJson(BoardTask task) {
        return RestJson.write(toMap(task));
    }

    public static String listJson(BoardListing listing, BoardCounts counts) {
        return listJson(listing, counts, 0L);
    }

    public static String listJson(BoardListing listing, BoardCounts counts, long sinceTick) {
        List<Object> items = new ArrayList<>(listing.tasks().size());
        for (BoardTask task : listing.tasks()) {
            items.add(toMap(task));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tasks", items);
        body.put("total", listing.total());
        body.put("offset", listing.offset());
        body.put("limit", listing.limit());
        body.put("sinceTick", sinceTick);
        body.put("groups", groupNames(listing.groups()));
        body.put("version", listing.version());
        if (listing.nextCursor() != null) {
            body.put("nextCursor", listing.nextCursor());
        }
        body.put("counts", Map.of("done", counts.done(), "reopened", counts.reopened()));
        return RestJson.write(body);
    }

    private static Map<String, Integer> groupNames(Map<BoardTileGroup, Integer> groups) {
        Map<String, Integer> named = new LinkedHashMap<>();
        for (BoardTileGroup group : BoardTileGroup.values()) {
            named.put(group.name(), groups.getOrDefault(group, 0));
        }
        return named;
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
