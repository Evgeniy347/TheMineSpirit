package com.theminespirit.core.rest;

import com.theminespirit.core.ui.BoardSnapshot;
import com.theminespirit.core.ui.BoardTileGroup;
import com.theminespirit.core.ui.BoardVersion;
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

public final class BoardListings {
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
            BoardTileGroup group = BoardSnapshot.groupOf(task, unfinished);
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
        return new BoardListing(page, total, from, query.limit(), groups, nextCursor, BoardVersion.ofTasks(sorted));
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
