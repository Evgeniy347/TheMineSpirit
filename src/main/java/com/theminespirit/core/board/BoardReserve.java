package com.theminespirit.core.board;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Бронь материалов доски: предметы, которые ждут открытые задания (в очереди, ждут ресурсы, выданы). Фоновые
 * занятия интеллекта видят запас за вычетом брони — иначе крафт палок съедал доски, собранные для сундуков
 * («craft chest: jungle_planks 1 из 9»), и этап 1 вставал. Задания-занятия ничего не бронируют.
 */
public final class BoardReserve {
    private static final Set<BoardStatus> OPEN =
            EnumSet.of(BoardStatus.PENDING, BoardStatus.WAITING_RESOURCES, BoardStatus.ASSIGNED);

    private BoardReserve() {}

    // Сколько каждого предмета (короткий id, без «minecraft:») держат открытые задания доски.
    public static Map<String, Integer> items(Board board) {
        Objects.requireNonNull(board, "Бронь без доски не считается: материалы держат её задания");
        Map<String, Integer> reserved = new HashMap<>();
        for (BoardTicket ticket : board.all()) {
            if (BoardActivities.isActivity(ticket.boardCase())
                    || !OPEN.contains(board.statusOf(ticket.id()).orElse(BoardStatus.DONE))) {
                continue;
            }
            ticket.requiredItems().forEach((item, count) -> reserved.merge(shortId(item), count, Integer::sum));
        }
        return Map.copyOf(reserved);
    }

    // Запас для источников заданий доски (первые шаги, факелы, запасы): за вычетом брони всех открытых заданий —
    // иначе уголь для факелов пережигал брёвна, взятые под забор (решение 54b п.5).
    public static StockView unreserved(StockView stock, Board board) {
        Objects.requireNonNull(stock, "Запас не может быть null: пустой задают пустым StockView");
        return new StockView(free(stock.available(), items(board)));
    }

    // Запас за вычетом брони: id запаса могут быть полными («minecraft:stick»), бронь — короткими.
    public static Map<String, Integer> free(Map<String, Integer> stock, Map<String, Integer> reserved) {
        Objects.requireNonNull(stock, "Запас не может быть null: пустой задают пустой картой");
        Objects.requireNonNull(reserved, "Бронь не может быть null: пустую задают пустой картой");
        Map<String, Integer> left = new HashMap<>(reserved);
        Map<String, Integer> free = new HashMap<>();
        stock.forEach((item, count) -> {
            String id = shortId(item);
            int held = Math.min(count, left.getOrDefault(id, 0));
            if (held > 0) {
                left.merge(id, -held, Integer::sum);
            }
            if (count - held > 0) {
                free.merge(item, count - held, Integer::sum);
            }
        });
        return Map.copyOf(free);
    }

    private static String shortId(String item) {
        return item.startsWith("minecraft:") ? item.substring("minecraft:".length()) : item;
    }
}
