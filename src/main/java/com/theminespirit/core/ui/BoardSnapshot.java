package com.theminespirit.core.ui;

import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.rest.BoardTask;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public final class BoardSnapshot {
    private static final int SHORT_UUID = 8;
    private static final Comparator<Row> ORDER = Comparator.<Row, BoardTileGroup>comparing(Row::group)
            .thenComparing(BoardSnapshot::unfinished)
            .thenComparingLong(Row::doneAt)
            .thenComparing(Comparator.<Row>comparingInt(row -> row.task().effectivePriority())
                    .reversed())
            .thenComparingLong(row -> row.task().createdAt())
            .thenComparing(row -> row.task().id());

    private BoardSnapshot() {}

    public static List<BoardTile> build(List<BoardTask> tasks, Function<String, @Nullable String> spiritNames) {
        return build(tasks, spiritNames, Map.of());
    }

    public static List<BoardTile> build(
            List<BoardTask> tasks,
            Function<String, @Nullable String> spiritNames,
            Map<String, List<String>> targetDetails) {
        Objects.requireNonNull(spiritNames, "spiritNames не может быть null: без него плитка не назовёт духа");
        Objects.requireNonNull(
                targetDetails, "targetDetails не может быть null: без целей задание задают пустой картой");
        List<Row> rows = rows(tasks);
        List<BoardTile> tiles = new ArrayList<>(rows.size());
        for (Row row : rows) {
            tiles.add(tile(row, spiritNames, targetDetails));
        }
        return List.copyOf(tiles);
    }

    public static List<Row> rows(List<BoardTask> tasks) {
        Objects.requireNonNull(tasks, "tasks не может быть null: пустая доска — пустой список");
        Set<String> unfinished = new HashSet<>();
        for (BoardTask task : tasks) {
            if (!task.done()) {
                unfinished.add(task.id());
            }
        }
        List<Row> rows = new ArrayList<>(tasks.size());
        for (BoardTask task : tasks) {
            BoardTileGroup group = groupOf(task, unfinished);
            long doneAt = 0L;
            if (group == BoardTileGroup.DONE && task.done()) {
                doneAt = task.finishedAt() > 0 ? task.finishedAt() : task.createdAt();
            }
            rows.add(new Row(task, group, doneAt));
        }
        rows.sort(ORDER);
        return List.copyOf(rows);
    }

    public static BoardTile tile(
            Row row, Function<String, @Nullable String> spiritNames, Map<String, List<String>> targetDetails) {
        Objects.requireNonNull(row, "Строка доски не может быть null: из неё строится плитка");
        Objects.requireNonNull(spiritNames, "spiritNames не может быть null: без него плитка не назовёт духа");
        Objects.requireNonNull(
                targetDetails, "targetDetails не может быть null: без целей задание задают пустой картой");
        BoardTask task = row.task();
        return new BoardTile(
                task.id(),
                task.boardCase(),
                task.level(),
                task.effectivePriority(),
                nameOf(task.assignee(), spiritNames),
                task.status(),
                row.group(),
                task.missing(),
                task.dependsOn(),
                row.doneAt(),
                task.startedAt(),
                task.finishedAt(),
                task.durationTicks(),
                task.supplyItem(),
                task.supplyCount(),
                detailsOf(task.modifiers(), targetDetails.get(task.id())));
    }

    private static boolean unfinished(Row row) {
        return row.group() == BoardTileGroup.DONE
                && !BoardStatus.DONE.name().equals(row.task().status());
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

    private static List<String> detailsOf(List<String> modifiers, @Nullable List<String> target) {
        if (target == null || target.isEmpty()) {
            return modifiers;
        }
        List<String> details = new ArrayList<>(modifiers);
        Set<String> keys = new HashSet<>();
        for (String modifier : modifiers) {
            keys.add(keyOf(modifier));
        }
        for (String detail : target) {
            if (keys.add(keyOf(detail))) {
                details.add(detail);
            }
        }
        return List.copyOf(details);
    }

    private static String keyOf(String detail) {
        int equals = detail.indexOf('=');
        return equals < 0 ? detail : detail.substring(0, equals);
    }

    static @Nullable String nameOf(@Nullable String assignee, Function<String, @Nullable String> names) {
        if (assignee == null) {
            return null;
        }
        String name = names.apply(assignee);
        if (name != null && !name.isBlank()) {
            return name;
        }
        return assignee.length() > SHORT_UUID ? assignee.substring(0, SHORT_UUID) : assignee;
    }

    public record Row(BoardTask task, BoardTileGroup group, long doneAt) {
        public Row {
            Objects.requireNonNull(task, "Задание строки не может быть null: строку строят по заданию доски");
            Objects.requireNonNull(group, "Группа строки не может быть null: по ней строка встаёт в колонку");
            if (doneAt < 0) {
                throw new IllegalArgumentException(
                        "Тик завершения " + doneAt + " отрицателен: завершение не бывает до начала отсчёта");
            }
        }
    }
}
