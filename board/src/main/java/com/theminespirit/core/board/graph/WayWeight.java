package com.theminespirit.core.board.graph;

import java.util.Map;

final class WayWeight {
    private WayWeight() {}

    static Map<String, Integer> checked(String id, double weight, Map<String, Integer> consumes) {
        if (!(weight > 0)) {
            throw new IllegalArgumentException("Путь " + id + " с весом " + weight
                    + ": стартовый вес пути обязан быть положительным, иначе путь никогда не выбирается");
        }
        return Map.copyOf(consumes);
    }
}
