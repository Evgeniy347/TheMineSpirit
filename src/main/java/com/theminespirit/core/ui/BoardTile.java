package com.theminespirit.core.ui;

import com.theminespirit.core.board.BoardStatus;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record BoardTile(
        String id,
        String boardCase,
        int level,
        int priority,
        @Nullable String spirit,
        String status,
        BoardTileGroup group,
        Map<String, Integer> missing,
        List<String> dependsOn,
        long doneAt,
        long startedAt,
        long finishedAt,
        long durationTicks,
        @Nullable String supplyItem,
        int supplyCount) {
    public BoardTile(
            String id,
            String boardCase,
            int level,
            int priority,
            @Nullable String spirit,
            String status,
            BoardTileGroup group,
            Map<String, Integer> missing,
            List<String> dependsOn,
            long doneAt,
            long startedAt,
            long finishedAt,
            long durationTicks) {
        this(
                id,
                boardCase,
                level,
                priority,
                spirit,
                status,
                group,
                missing,
                dependsOn,
                doneAt,
                startedAt,
                finishedAt,
                durationTicks,
                null,
                0);
    }

    public BoardTile {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id плитки пустое: по нему экран отличает задания доски");
        }
        Objects.requireNonNull(boardCase, "Кейс плитки не может быть null: по нему клиент пишет текст задания");
        Objects.requireNonNull(status, "Статус плитки не может быть null: по нему экран подписывает плитку");
        Objects.requireNonNull(group, "Группа плитки не может быть null: по ней экран упорядочивает плитки");
        missing = Map.copyOf(Objects.requireNonNull(
                missing, "missing не может быть null: задание без нехватки задают пустой картой"));
        dependsOn = List.copyOf(Objects.requireNonNull(
                dependsOn, "dependsOn не может быть null: задание без зависимостей задают пустым списком"));
        if (level < 1) {
            throw new IllegalArgumentException("Уровень плитки " + level + " меньше 1: уровни заданий начинаются с 1");
        }
        if (doneAt < 0) {
            throw new IllegalArgumentException("doneAt равен " + doneAt + ": тик завершения не бывает отрицательным");
        }
        if (startedAt < 0 || finishedAt < 0 || durationTicks < 0) {
            throw new IllegalArgumentException("Время плитки " + startedAt + "/" + finishedAt + "/" + durationTicks
                    + " отрицательно: тики и затрата идут вперёд, а не назад");
        }
        if (supplyCount < 0) {
            throw new IllegalArgumentException(
                    "Количество материала " + supplyCount + " отрицательно: запас задания не бывает меньше нуля");
        }
    }

    public boolean completed() {
        return group == BoardTileGroup.DONE && BoardStatus.DONE.name().equals(status);
    }

    public boolean unfinished() {
        return group == BoardTileGroup.DONE && !completed();
    }
}
