package com.theminespirit.core.board;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Задание доски: кейс, приоритет, владелец (конкретный дух или «любой свободный»),
 * уровень постройки, причина (ключ актуальности), требуемые ресурсы (гейт),
 * зависимости (id заданий-предшественников). Срока жизни нет: актуальность проверяется
 * причиной при каждой пересборке, а не смертью по таймеру.
 * Неизменяем: доска хранит билеты в очереди и отдаёт наблюдателю, подмена полей
 * сломала бы и сортировку, и гейт.
 */
public record BoardTicket(
        String id,
        BoardCase boardCase,
        Optional<UUID> assignee,
        int priority,
        long createdAt,
        int level,
        String cause,
        List<String> dependsOn,
        Map<String, Integer> requiredItems,
        List<String> modifiers) {

    public static final String ALWAYS_CAUSE = "always";

    public BoardTicket(
            String id,
            BoardCase boardCase,
            Optional<UUID> assignee,
            int priority,
            long createdAt,
            int level,
            String cause,
            List<String> dependsOn,
            Map<String, Integer> requiredItems) {
        this(id, boardCase, assignee, priority, createdAt, level, cause, dependsOn, requiredItems, List.of());
    }

    public BoardTicket {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id задания пустое: по id доска находит "
                    + "задание в очереди, связывает зависимости и отдаёт наблюдателю");
        }
        Objects.requireNonNull(
                boardCase,
                "Кейс задания не может быть null: без него доска " + "не знает приоритет, гейт и план выдачи");
        if (createdAt < 0) {
            throw new IllegalArgumentException("createdAt равен " + createdAt
                    + " и отрицателен: время постановки задания не может быть до начала отсчёта");
        }
        if (level < 1) {
            throw new IllegalArgumentException("Уровень " + level + " меньше 1: первая постройка "
                    + "ставится уровнем 1, нулевого уровня не существует");
        }
        if (cause == null || cause.isBlank()) {
            throw new IllegalArgumentException("Причина задания пуста: по ней пересборка проверяет "
                    + "актуальность («воронка ещё есть?»), постоянное задание задают причиной "
                    + ALWAYS_CAUSE);
        }
        if (priority < 0 || priority > BoardTuning.MAX_PRIORITY) {
            throw new IllegalArgumentException("Приоритет " + priority + " вне диапазона [0, "
                    + BoardTuning.MAX_PRIORITY + "]: доска сравнивает задания с ремонтом, "
                    + "выход за границу сломает сортировку выдачи");
        }
        Objects.requireNonNull(
                assignee, "assignee не может быть null: «любой свободный дух» " + "задают пустым Optional, а не null");
        dependsOn = List.copyOf(Objects.requireNonNull(
                dependsOn, "dependsOn не может быть null: задание без зависимостей задают пустым списком"));
        if (dependsOn.contains(id)) {
            throw new IllegalArgumentException("Задание '" + id + "' зависит от самого себя: "
                    + "самозависимость навсегда блокирует выдачу — проверьте источник задания");
        }
        requiredItems = Map.copyOf(Objects.requireNonNull(
                requiredItems, "requiredItems не может быть null: задание без ресурсов задают пустой картой"));
        modifiers = List.copyOf(Objects.requireNonNull(
                modifiers, "modifiers не может быть null: задание без модификаторов задают пустым списком"));
        for (String modifier : modifiers) {
            if (modifier == null || modifier.isBlank()) {
                throw new IllegalArgumentException("Сработавший модификатор назван пустой строкой: "
                        + "наблюдатель показывает ключи, а не пустоту");
            }
        }
    }
}
