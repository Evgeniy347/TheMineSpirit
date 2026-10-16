package com.theminespirit.core.board;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Задание доски: кейс, приоритет, владелец (конкретный дух или «любой свободный»),
 * требуемые ресурсы (гейт), зависимости (id заданий-предшественников), срок жизни.
 * Неизменяем: доска хранит билеты в очереди и отдаёт наблюдателю, подмена полей
 * сломала бы и сортировку, и гейт.
 */
public record BoardTicket(String id, BoardCase boardCase, Optional<UUID> assignee, int priority,
        long createdAt, long ttlTicks, List<String> dependsOn, Map<String, Integer> requiredItems) {

    public BoardTicket {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id задания пустое: по id доска находит "
                    + "задание в очереди, связывает зависимости и отдаёт наблюдателю");
        }
        Objects.requireNonNull(boardCase, "Кейс задания не может быть null: без него доска "
                + "не знает приоритет, гейт и план выдачи");
        if (createdAt < 0) {
            throw new IllegalArgumentException("createdAt равен " + createdAt
                    + " и отрицателен: время постановки задания не может быть до начала отсчёта");
        }
        if (ttlTicks <= 0) {
            throw new IllegalArgumentException("ttlTicks равен " + ttlTicks
                    + " и не положителен: задание без срока жизни зависло бы в очереди навсегда");
        }
        if (priority < 0 || priority > BoardTuning.MAX_PRIORITY) {
            throw new IllegalArgumentException("Приоритет " + priority + " вне диапазона [0, "
                    + BoardTuning.MAX_PRIORITY + "]: доска сравнивает задания с ремонтом, "
                    + "выход за границу сломает сортировку выдачи");
        }
        Objects.requireNonNull(assignee, "assignee не может быть null: «любой свободный дух» "
                + "задают пустым Optional, а не null");
        dependsOn = List.copyOf(Objects.requireNonNull(dependsOn,
                "dependsOn не может быть null: задание без зависимостей задают пустым списком"));
        if (dependsOn.contains(id)) {
            throw new IllegalArgumentException("Задание '" + id + "' зависит от самого себя: "
                    + "самозависимость навсегда блокирует выдачу — проверьте источник задания");
        }
        requiredItems = Map.copyOf(Objects.requireNonNull(requiredItems,
                "requiredItems не может быть null: задание без ресурсов задают пустой картой"));
    }
}
