package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record BoardState(
        List<Entry> entries, Set<String> ledger, List<Blocked> blocked, List<Pause> pauses, List<Stuck> stuck) {

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

    public record Blocked(String ticketId, Map<String, Integer> missing, Map<String, Integer> available, long since) {
        public Blocked {
            Objects.requireNonNull(ticketId, "id заблокированного задания не может быть null: пауза адресная");
            missing = Map.copyOf(Objects.requireNonNull(missing, "Нехватка блокировки не может быть null"));
            available = Map.copyOf(Objects.requireNonNull(available, "Запас блокировки не может быть null"));
            if (since < 0) {
                throw new IllegalArgumentException("Тик блокировки " + since + " отрицателен: пауза идёт вперёд");
            }
        }
    }

    public record Pause(
            UUID spirit,
            BoardCase boardCase,
            int consecutive,
            long pausedUntil,
            boolean detached,
            @Nullable String token) {
        public Pause {
            Objects.requireNonNull(spirit, "Дух паузы не может быть null: пауза считается на конкретного духа");
            Objects.requireNonNull(boardCase, "Кейс паузы не может быть null: пауза считается на конкретный кейс");
            if (consecutive < 0 || pausedUntil < 0) {
                throw new IllegalArgumentException("Пауза кейса " + boardCase + " отрицательна: счёт идёт вперёд");
            }
        }
    }

    public record Stuck(UUID spirit, @Nullable BlockCoord at, long since) {
        public Stuck {
            Objects.requireNonNull(spirit, "Дух застревания не может быть null: метка адресная");
            if (since < 0) {
                throw new IllegalArgumentException("Тик застревания " + since + " отрицателен: метка идёт вперёд");
            }
        }
    }

    public BoardState {
        entries = List.copyOf(Objects.requireNonNull(
                entries, "Список билетов не может быть null: пустой снимок задают пустым списком"));
        ledger = Set.copyOf(Objects.requireNonNull(
                ledger, "Отметки ledger не могут быть null: пустой снимок задают пустым набором"));
        blocked = List.copyOf(Objects.requireNonNull(blocked, "Блокировки не могут быть null"));
        pauses = List.copyOf(Objects.requireNonNull(pauses, "Паузы кейсов не могут быть null"));
        stuck = List.copyOf(Objects.requireNonNull(stuck, "Метки застревания не могут быть null"));
        Set<String> ids = new HashSet<>();
        for (Entry entry : entries) {
            if (!ids.add(entry.ticket().id())) {
                throw new IllegalArgumentException("Билет '" + entry.ticket().id()
                        + "' повторён в снимке: дубликат id сломал бы поиск задания после загрузки");
            }
        }
    }

    public BoardState(List<Entry> entries, Set<String> ledger) {
        this(entries, ledger, List.of(), List.of(), List.of());
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
