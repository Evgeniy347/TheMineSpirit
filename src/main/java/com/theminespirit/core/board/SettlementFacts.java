package com.theminespirit.core.board;

import java.util.Objects;
import java.util.Set;

/**
 * Факты о поселении для генератора первых шагов: есть ли дом, сундуки, верстак,
 * инструменты (запас цепочки), вход в шахту, забор, освещение, постройки. Собирает адаптер
 * из снимка мира; генератор читает только эти данные.
 */
public record SettlementFacts(boolean hasHome, boolean groundsCleared, int chests,
        boolean craftingStation, Set<String> tools, boolean mineEntrance, boolean fence,
        boolean lighting, boolean houses) {

    public SettlementFacts {
        if (chests < 0) {
            throw new IllegalArgumentException("chests равен " + chests
                    + " и отрицателен: число сундуков не может быть отрицательным");
        }
        tools = Set.copyOf(Objects.requireNonNull(tools,
                "tools не может быть null: набор собранных инструментов задают пустым множеством, а не null"));
    }
}
