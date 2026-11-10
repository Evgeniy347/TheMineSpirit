package com.theminespirit.core.board;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class BoardReserve {
    private BoardReserve() {}

    public static Map<String, Integer> items(Board board) {
        Objects.requireNonNull(board, "Бронь без доски не считается: материалы держат её задания");
        Map<String, Integer> reserved = new HashMap<>();
        for (BoardTicket ticket : board.tickets()) {
            if (!board.statusOf(ticket.id()).orElse(BoardStatus.DONE).open()) {
                continue;
            }
            ticket.requiredItems().forEach((item, count) -> reserved.merge(shortId(item), count, Integer::sum));
        }
        return Map.copyOf(reserved);
    }

    public static StockView unreserved(StockView stock, Board board) {
        Objects.requireNonNull(stock, "Запас не может быть null: пустой задают пустым StockView");
        return new StockView(free(stock.available(), items(board)));
    }

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
