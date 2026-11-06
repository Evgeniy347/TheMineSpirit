package com.theminespirit.core.board.graph;

import java.util.List;
import java.util.Optional;

public final class StoreTransfer {
    private static final int MOVE_LIMIT = 16;

    public record Move(GraphFacts.Position from, GraphFacts.Position to, String item, int count) {
        public Move {
            if (item == null || item.isBlank()) {
                throw new IllegalArgumentException("Предмет переноса пуст: нечего нести из сундука в сундук");
            }
            if (count < 1) {
                throw new IllegalArgumentException("Количество переноса " + count + " меньше 1: переносить нечего");
            }
        }
    }

    private StoreTransfer() {}

    public static boolean hasFreeChest(List<GraphFacts.Chest> chests) {
        return chests.stream().anyMatch(chest -> chest.freeSlots() > 0);
    }

    public static Optional<Move> chestMove(List<GraphFacts.Chest> chests) {
        GraphFacts.Chest from = null;
        for (GraphFacts.Chest chest : chests) {
            if (chest.freeSlots() > 0 || chest.contents().isEmpty()) {
                continue;
            }
            if (from == null || count(from) < count(chest)) {
                from = chest;
            }
        }
        if (from == null) {
            return Optional.empty();
        }
        GraphFacts.Chest to = null;
        for (GraphFacts.Chest chest : chests) {
            if (chest.freeSlots() <= 0 || chest.position().equals(from.position())) {
                continue;
            }
            if (to == null || to.freeSlots() < chest.freeSlots()) {
                to = chest;
            }
        }
        if (to == null) {
            return Optional.empty();
        }
        String item = null;
        int amount = 0;
        for (var entry : from.contents().entrySet()) {
            if (entry.getValue() > amount) {
                item = entry.getKey();
                amount = entry.getValue();
            }
        }
        if (item == null) {
            return Optional.empty();
        }
        return Optional.of(new Move(from.position(), to.position(), item, Math.min(amount, MOVE_LIMIT)));
    }

    private static int count(GraphFacts.Chest chest) {
        return chest.contents().values().stream().mapToInt(Integer::intValue).sum();
    }
}
