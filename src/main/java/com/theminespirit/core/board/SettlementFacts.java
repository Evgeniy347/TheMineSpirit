package com.theminespirit.core.board;

/**
 * Факты о поселении для генератора первых шагов: есть ли дом, сундуки, верстак,
 * инструменты, вход в шахту, забор, освещение, постройки. Собирает адаптер
 * из снимка мира; генератор читает только эти данные.
 */
public record SettlementFacts(boolean hasHome, boolean groundsCleared, int chests,
        boolean craftingStation, boolean tools, boolean mineEntrance, boolean fence,
        boolean lighting, boolean houses) {

    public SettlementFacts {
        if (chests < 0) {
            throw new IllegalArgumentException("chests равен " + chests
                    + " и отрицателен: число сундуков не может быть отрицательным");
        }
    }
}
