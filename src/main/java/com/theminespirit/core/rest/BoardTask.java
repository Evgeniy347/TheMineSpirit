package com.theminespirit.core.rest;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Задание доски глазами наблюдателя REST: кейс, приоритет, владелец, статус, гейт и зависимости.
 * Собирается адаптером доски, отдаётся стратегу и игроку; {@code assignee} пуст, когда задание
 * ждёт любого свободного духа.
 */
public record BoardTask(String id, String boardCase, int priority, String assignee, String status,
        Map<String, Integer> requiredItems, Map<String, Integer> missing, List<String> dependsOn,
        long createdAt, long ttlTicks) {

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
            throw new IllegalArgumentException("Приоритет " + priority + " отрицателен: доска не выдаёт "
                    + "заданий с отрицательным приоритетом");
        }
        if (createdAt < 0) {
            throw new IllegalArgumentException("createdAt равен " + createdAt + " и отрицателен: время постановки "
                    + "не может быть до начала отсчёта");
        }
        if (ttlTicks <= 0) {
            throw new IllegalArgumentException("ttlTicks равен " + ttlTicks + " и не положителен: у задания "
                    + "на доске всегда есть срок жизни или метка «без TTL»");
        }
        requiredItems = Map.copyOf(Objects.requireNonNull(requiredItems,
                "requiredItems не может быть null: задание без ресурсов задают пустой картой"));
        missing = Map.copyOf(Objects.requireNonNull(missing,
                "missing не может быть null: задание без нехватки задают пустой картой"));
        dependsOn = List.copyOf(Objects.requireNonNull(dependsOn,
                "dependsOn не может быть null: задание без зависимостей задают пустым списком"));
    }
}
