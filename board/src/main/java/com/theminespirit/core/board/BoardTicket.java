package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

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
        List<String> modifiers,
        List<UUID> executors) {

    public static final String ALWAYS_CAUSE = "always";

    public boolean fromGraph() {
        return cause.startsWith("graph:");
    }

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
        this(
                id,
                boardCase,
                assignee,
                priority,
                createdAt,
                level,
                cause,
                dependsOn,
                requiredItems,
                List.of(),
                List.of());
    }

    public BoardTicket(
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
        this(
                id,
                boardCase,
                assignee,
                priority,
                createdAt,
                level,
                cause,
                dependsOn,
                requiredItems,
                modifiers,
                List.of());
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
        executors = List.copyOf(Objects.requireNonNull(
                executors, "executors не может быть null: занятие без команды задают пустым списком"));
        if (executors.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "В команде задания '" + id + "' есть null-дух: " + "каждый исполнитель — конкретный участник");
        }
        if (!executors.isEmpty() && assignee.isEmpty()) {
            throw new IllegalArgumentException("У задания '" + id + "' есть команда, но нет лидера: "
                    + "assignee называет ведущего, executors — остальных участников");
        }
        if (executors.size() != executors.stream().distinct().count()) {
            throw new IllegalArgumentException(
                    "В команде задания '" + id + "' повторён дух: " + "каждый участник идёт один раз");
        }
    }

    public BoardTicket withPriority(int next) {
        if (next < 0 || next > BoardTuning.MAX_PRIORITY) {
            throw new IllegalArgumentException("Приоритет " + next + " вне диапазона [0, " + BoardTuning.MAX_PRIORITY
                    + "]: доска сравнивает задания с ремонтом");
        }
        return new BoardTicket(
                id, boardCase, assignee, next, createdAt, level, cause, dependsOn, requiredItems, modifiers, executors);
    }

    public BoardTicket withModifier(String modifier) {
        Objects.requireNonNull(modifier, "Модификатор не может быть null: пустой список задают List.of()");
        List<String> details = new ArrayList<>(modifiers);
        details.add(modifier);
        return new BoardTicket(
                id,
                boardCase,
                assignee,
                priority,
                createdAt,
                level,
                cause,
                dependsOn,
                requiredItems,
                details,
                executors);
    }

    public BoardTicket withFailure(@Nullable String reason) {
        if (reason == null || reason.isBlank()) {
            return this;
        }
        List<String> details = new ArrayList<>(modifiers);
        details.add("reason=" + reason);
        return new BoardTicket(
                id,
                boardCase,
                assignee,
                priority,
                createdAt,
                level,
                cause,
                dependsOn,
                requiredItems,
                details,
                executors);
    }

    public boolean sameDetails(BoardTicket other) {
        return level == other.level
                && cause.equals(other.cause)
                && dependsOn.equals(other.dependsOn)
                && requiredItems.equals(other.requiredItems)
                && executors.equals(other.executors);
    }

    public boolean teamwork() {
        return !executors.isEmpty();
    }

    public boolean inCrew(UUID spirit) {
        Objects.requireNonNull(spirit, "Дух команды не может быть null: участник — конкретный дух");
        return assignee.filter(spirit::equals).isPresent() || executors.contains(spirit);
    }

    public boolean replacesDone(BoardTicket before, boolean stage) {
        return !stage && (ALWAYS_CAUSE.equals(cause) || !sameDetails(before));
    }
}
