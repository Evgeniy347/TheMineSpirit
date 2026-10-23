package com.theminespirit.core.ui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Плитка экрана доски: что передаёт сервер клиенту про одно задание. {@code boardCase} — имя кейса
 * доски (клиент переводит его ключом языка), {@code spirit} — имя духа-исполнителя или null, когда
 * задание ещё никому не выдано; {@code dependsOn} — id заданий, без завершения которых задание не
 * начнётся; {@code doneAt} — тик завершения (0, если задание не завершено).
 */
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
        long doneAt) {
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
    }
}
