package com.theminespirit.core.ui;

import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.rest.BoardTask;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

public final class BoardSnapshot {
    private static final int SHORT_UUID = 8;
    private static final Comparator<Ranked> ORDER = Comparator.<Ranked, BoardTileGroup>comparing(
                    entry -> entry.tile().group())
            .thenComparing(entry -> entry.tile().unfinished())
            .thenComparingLong(entry -> entry.tile().doneAt())
            .thenComparing(Comparator.<Ranked>comparingInt(entry -> entry.tile().priority())
                    .reversed())
            .thenComparingLong(Ranked::createdAt)
            .thenComparing(entry -> entry.tile().id());

    private BoardSnapshot() {}

    public static List<BoardTile> build(List<BoardTask> tasks, Function<String, @Nullable String> spiritNames) {
        Objects.requireNonNull(tasks, "tasks не может быть null: пустая доска — пустой список");
        Objects.requireNonNull(spiritNames, "spiritNames не может быть null: без него плитка не назовёт духа");
        Set<String> unfinished = new HashSet<>();
        for (BoardTask task : tasks) {
            if (!task.done()) {
                unfinished.add(task.id());
            }
        }
        List<Ranked> ranked = new ArrayList<>(tasks.size());
        for (BoardTask task : tasks) {
            BoardTileGroup group = groupOf(task, unfinished);
            long doneAt = 0L;
            if (group == BoardTileGroup.DONE && task.done()) {
                doneAt = task.finishedAt() > 0 ? task.finishedAt() : task.createdAt();
            }
            BoardTile tile = new BoardTile(
                    task.id(),
                    task.boardCase(),
                    task.level(),
                    task.effectivePriority(),
                    nameOf(task.assignee(), spiritNames),
                    task.status(),
                    group,
                    task.missing(),
                    task.dependsOn(),
                    doneAt,
                    task.startedAt(),
                    task.finishedAt(),
                    task.durationTicks(),
                    task.supplyItem(),
                    task.supplyCount(),
                    task.modifiers());
            ranked.add(new Ranked(tile, task.createdAt()));
        }
        ranked.sort(ORDER);
        List<BoardTile> tiles = new ArrayList<>(ranked.size());
        for (Ranked entry : ranked) {
            tiles.add(entry.tile());
        }
        return List.copyOf(tiles);
    }

    public static BoardTileGroup groupOf(BoardTask task, Set<String> unfinishedIds) {
        Objects.requireNonNull(task, "task не может быть null: группу определяют по заданию");
        Objects.requireNonNull(unfinishedIds, "unfinishedIds не может быть null: нет заданий — пустое множество");
        if (task.done()) {
            return BoardTileGroup.DONE;
        }
        String status = task.status();
        if (BoardStatus.ASSIGNED.name().equals(status)) {
            return BoardTileGroup.ACTIVE;
        }
        if (!BoardStatus.PENDING.name().equals(status) || !task.missing().isEmpty()) {
            return BoardTileGroup.DONE;
        }
        for (String dependency : task.dependsOn()) {
            if (unfinishedIds.contains(dependency)) {
                return BoardTileGroup.DONE;
            }
        }
        return BoardTileGroup.AVAILABLE;
    }

    private static @Nullable String nameOf(@Nullable String assignee, Function<String, @Nullable String> names) {
        if (assignee == null) {
            return null;
        }
        String name = names.apply(assignee);
        if (name != null && !name.isBlank()) {
            return name;
        }
        return assignee.length() > SHORT_UUID ? assignee.substring(0, SHORT_UUID) : assignee;
    }

    private record Ranked(BoardTile tile, long createdAt) {}
}
