package com.theminespirit.core.rest;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Задание доски глазами наблюдателя REST: кейс, приоритет, владелец, статус, гейт и зависимости.
 * Собирается адаптером доски, отдаётся стратегу и игроку; {@code assignee} пуст, когда задание
 * ждёт любого свободного духа. {@code startedAt} и {@code finishedAt} — тики выдачи и закрытия из
 * журнала (0 — события не было), {@code durationTicks} — затраченное время на момент наблюдения.
 */
public record BoardTask(
        String id,
        String boardCase,
        int priority,
        @Nullable String assignee,
        String status,
        Map<String, Integer> requiredItems,
        Map<String, Integer> missing,
        List<String> dependsOn,
        long createdAt,
        int level,
        String cause,
        int effectivePriority,
        List<String> modifiers,
        boolean done,
        long startedAt,
        long finishedAt,
        long durationTicks,
        @Nullable String waitReason) {

    public BoardTask(
            String id,
            String boardCase,
            int priority,
            @Nullable String assignee,
            String status,
            Map<String, Integer> requiredItems,
            Map<String, Integer> missing,
            List<String> dependsOn,
            long createdAt,
            int level,
            String cause,
            int effectivePriority,
            List<String> modifiers,
            boolean done,
            long startedAt,
            long finishedAt,
            long durationTicks) {
        this(
                id,
                boardCase,
                priority,
                assignee,
                status,
                requiredItems,
                missing,
                dependsOn,
                createdAt,
                level,
                cause,
                effectivePriority,
                modifiers,
                done,
                startedAt,
                finishedAt,
                durationTicks,
                null);
    }

    public BoardTask(
            String id,
            String boardCase,
            int priority,
            @Nullable String assignee,
            String status,
            Map<String, Integer> requiredItems,
            Map<String, Integer> missing,
            List<String> dependsOn,
            long createdAt,
            int level,
            String cause,
            int effectivePriority,
            List<String> modifiers,
            boolean done) {
        this(
                id,
                boardCase,
                priority,
                assignee,
                status,
                requiredItems,
                missing,
                dependsOn,
                createdAt,
                level,
                cause,
                effectivePriority,
                modifiers,
                done,
                0L,
                0L,
                0L,
                null);
    }

    public BoardTask(
            String id,
            String boardCase,
            int priority,
            String assignee,
            String status,
            Map<String, Integer> requiredItems,
            Map<String, Integer> missing,
            List<String> dependsOn,
            long createdAt,
            int level,
            String cause) {
        this(
                id,
                boardCase,
                priority,
                assignee,
                status,
                requiredItems,
                missing,
                dependsOn,
                createdAt,
                level,
                cause,
                priority,
                List.of(),
                false,
                0L,
                0L,
                0L);
    }

    public BoardTask {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id задания пуст: наблюдатель обязан назвать задание, "
                    + "иначе по ответу нечего отменять и не с чем сверять зависимости");
        }
        if (boardCase == null || boardCase.isBlank()) {
            throw new IllegalArgumentException("Кейс задания пуст: без него наблюдателю не видно, что делать");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Статус задания пуст: по нему наблюдатель решает, выдано оно или ждёт");
        }
        if (priority < 0) {
            throw new IllegalArgumentException(
                    "Приоритет " + priority + " отрицателен: доска не выдаёт " + "заданий с отрицательным приоритетом");
        }
        if (createdAt < 0) {
            throw new IllegalArgumentException("createdAt равен " + createdAt + " и отрицателен: время постановки "
                    + "не может быть до начала отсчёта");
        }
        if (startedAt < 0 || finishedAt < 0 || durationTicks < 0) {
            throw new IllegalArgumentException("Время задания " + startedAt + "/" + finishedAt + "/" + durationTicks
                    + " отрицательно: тики и затрата идут вперёд, а не назад");
        }
        if (level < 1) {
            throw new IllegalArgumentException(
                    "Уровень " + level + " меньше 1: первая постройка " + "ставится уровнем 1");
        }
        if (cause == null || cause.isBlank()) {
            throw new IllegalArgumentException("Причина задания пуста: по ней пересборка проверяет "
                    + "актуальность, постоянное задание задают причиной always");
        }
        requiredItems = Map.copyOf(Objects.requireNonNull(
                requiredItems, "requiredItems не может быть null: задание без ресурсов задают пустой картой"));
        missing = Map.copyOf(Objects.requireNonNull(
                missing, "missing не может быть null: задание без нехватки задают пустой картой"));
        dependsOn = List.copyOf(Objects.requireNonNull(
                dependsOn, "dependsOn не может быть null: задание без зависимостей задают пустым списком"));
        if (effectivePriority < 0) {
            throw new IllegalArgumentException("Эффективный приоритет " + effectivePriority
                    + " отрицателен: доска не выдаёт заданий с отрицательным приоритетом");
        }
        modifiers = List.copyOf(Objects.requireNonNull(
                modifiers, "modifiers не может быть null: задание без модификаторов задают пустым списком"));
    }
}
