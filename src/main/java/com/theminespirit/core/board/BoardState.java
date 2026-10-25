package com.theminespirit.core.board;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record BoardState(List<Entry> entries, Set<String> ledger) {

    public record Entry(
            BoardTicket ticket,
            BoardStatus status,
            @Nullable UUID holder,
            @Nullable Long failedAt,
            int failures) {
        public Entry {
            Objects.requireNonNull(ticket, "Билет в снимке доски не может быть null: восстанавливать нечего");
            Objects.requireNonNull(status, "Статус билета в снимке не может быть null: доска хранит его явно");
            if (failures < 0) {
                throw new IllegalArgumentException(
                        "Счётчик провалов " + failures + " отрицателен: доска считает провалы вперёд, а не назад");
            }
        }
    }

    public BoardState {
        entries = List.copyOf(Objects.requireNonNull(
                entries, "Список билетов не может быть null: пустой снимок задают пустым списком"));
        ledger = Set.copyOf(Objects.requireNonNull(
                ledger, "Отметки ledger не могут быть null: пустой снимок задают пустым набором"));
        Set<String> ids = new HashSet<>();
        for (Entry entry : entries) {
            if (!ids.add(entry.ticket().id())) {
                throw new IllegalArgumentException("Билет '" + entry.ticket().id()
                        + "' повторён в снимке: дубликат id сломал бы поиск задания после загрузки");
            }
        }
    }

    public static BoardState empty() {
        return new BoardState(List.of(), Set.of());
    }

    public boolean isEmpty() {
        return entries.isEmpty() && ledger.isEmpty();
    }

    public Optional<Entry> find(String ticketId) {
        Objects.requireNonNull(ticketId, "id задания не может быть null: по нему ищется запись снимка");
        return entries.stream()
                .filter(entry -> entry.ticket().id().equals(ticketId))
                .findFirst();
    }
}
