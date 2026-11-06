package com.theminespirit.core.rest;

import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.ui.BoardTileGroup;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public final class BoardListings {
    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final Comparator<BoardTask> ORDER =
            Comparator.comparingLong(BoardTask::createdAt).thenComparing(BoardTask::id);

    private BoardListings() {}

    public static BoardListing select(List<BoardTask> tasks, BoardListQuery query) {
        Objects.requireNonNull(tasks, "tasks не может быть null: пустая доска — пустой список");
        Objects.requireNonNull(query, "query не может быть null: страницу собирают по запросу");
        List<BoardTask> sorted = new ArrayList<>(tasks);
        sorted.sort(ORDER);
        Set<String> unfinished = new HashSet<>();
        for (BoardTask task : sorted) {
            if (!task.done()) {
                unfinished.add(task.id());
            }
        }
        Map<BoardTileGroup, Integer> groups = new EnumMap<>(BoardTileGroup.class);
        for (BoardTileGroup group : BoardTileGroup.values()) {
            groups.put(group, 0);
        }
        List<BoardTask> filtered = new ArrayList<>();
        for (BoardTask task : sorted) {
            if (!matches(task, query)) {
                continue;
            }
            BoardTileGroup group = groupOf(task, unfinished);
            groups.merge(group, 1, Integer::sum);
            if (query.group() != null && group != query.group()) {
                continue;
            }
            filtered.add(task);
        }
        int total = filtered.size();
        int from = Math.min(query.offset(), total);
        int to = Math.min(from + query.limit(), total);
        List<BoardTask> page = List.copyOf(filtered.subList(from, to));
        String nextCursor = to < total ? cursorOf(to) : null;
        return new BoardListing(page, total, from, query.limit(), groups, nextCursor, versionOf(sorted));
    }

    public static BoardTileGroup groupOf(BoardTask task, Set<String> unfinishedIds) {
        Objects.requireNonNull(task, "task не может быть null: группу определяют по заданию");
        Objects.requireNonNull(unfinishedIds, "unfinishedIds не может быть null: нет заданий — пустое множество");
        if (task.done() || BoardStatus.DONE.name().equals(task.status())) {
            return BoardTileGroup.DONE;
        }
        String status = task.status();
        if (BoardStatus.ASSIGNED.name().equals(status)) {
            return BoardTileGroup.ACTIVE;
        }
        if (!BoardStatus.PENDING.name().equals(status) || !task.missing().isEmpty()) {
            return BoardTileGroup.WAITING;
        }
        for (String dependency : task.dependsOn()) {
            if (unfinishedIds.contains(dependency)) {
                return BoardTileGroup.WAITING;
            }
        }
        return BoardTileGroup.AVAILABLE;
    }

    public static long versionOf(List<BoardTask> tasks) {
        Objects.requireNonNull(tasks, "tasks не может быть null: версия доски считается по её заданиям");
        long hash = FNV_OFFSET;
        for (BoardTask task : tasks) {
            hash = mix(hash, task.id());
            hash = mix(hash, task.boardCase());
            hash = mix(hash, task.status());
            hash = mix(hash, task.assignee() == null ? "-" : task.assignee());
            hash = mix(hash, Long.toString(task.createdAt()));
            hash = mix(hash, Boolean.toString(task.done()));
            hash = mix(hash, Integer.toString(task.level()));
            hash = mix(hash, Integer.toString(task.effectivePriority()));
            for (String dependency : task.dependsOn()) {
                hash = mix(hash, "d:" + dependency);
            }
            for (Map.Entry<String, Integer> entry : new TreeMap<>(task.missing()).entrySet()) {
                hash = mix(hash, "m:" + entry.getKey() + "=" + entry.getValue());
            }
        }
        return hash;
    }

    private static long mix(long hash, String value) {
        long next = hash;
        for (int index = 0; index < value.length(); index++) {
            next ^= value.charAt(index);
            next *= FNV_PRIME;
        }
        return (next ^ 0x1fL) * FNV_PRIME;
    }

    public static String cursorOf(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException(
                    "Смещение курсора " + offset + " отрицательно: курсор указывает вперёд по ленте");
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(("o:" + offset).getBytes(StandardCharsets.UTF_8));
    }

    public static int offsetOfCursor(String cursor) {
        Objects.requireNonNull(cursor, "Курсор не может быть null: пустой задают пустой строкой");
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor.trim()), StandardCharsets.UTF_8);
            if (!decoded.startsWith("o:")) {
                throw new IllegalArgumentException("нет префикса o:");
            }
            int offset = Integer.parseInt(decoded.substring(2));
            if (offset < 0) {
                throw new IllegalArgumentException("отрицательное смещение");
            }
            return offset;
        } catch (IllegalArgumentException broken) {
            throw new ApiException(
                    ErrorCode.INVALID_REQUEST,
                    "Курсор доски (cursor) '" + cursor + "' не разобран: пришлите nextCursor из прошлого ответа "
                            + "или используйте offset",
                    Map.of("field", "cursor"),
                    broken);
        }
    }

    private static boolean matches(BoardTask task, BoardListQuery query) {
        if (query.status() != null && !query.status().name().equals(task.status())) {
            return false;
        }
        if (query.assignee() != null && !query.assignee().equals(task.assignee())) {
            return false;
        }
        if (query.boardCase() != null && !query.boardCase().equals(task.boardCase())) {
            return false;
        }
        return task.createdAt() >= query.since();
    }
}
