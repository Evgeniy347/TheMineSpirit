package com.theminespirit.core.board;

import java.util.Objects;
import java.util.Set;

/**
 * Факты о поселении для генератора первых шагов: есть ли дом, сундуки, верстак, инструменты (запас цепочки), вход в
 * шахту, печь, забор, освещение, постройки, стол кузнеца и наковальня, а также шаги, сделанные по журналу доски
 * ({@code done}) и заблокированные провалами ({@code blocked}). Собирает адаптер из снимка мира и доски; генератор
 * читает только эти данные.
 */
public record SettlementFacts(
        boolean hasHome,
        boolean groundsCleared,
        int chests,
        boolean craftingStation,
        Set<String> tools,
        boolean mineEntrance,
        boolean furnace,
        boolean fence,
        boolean lighting,
        boolean houses,
        boolean smithingTable,
        boolean anvil,
        Set<BoardCase> done,
        Set<BoardCase> blocked) {

    public SettlementFacts {
        if (chests < 0) {
            throw new IllegalArgumentException(
                    "chests равен " + chests + " и отрицателен: число сундуков не может быть отрицательным");
        }
        tools = Set.copyOf(Objects.requireNonNull(
                tools, "tools не может быть null: набор собранных инструментов задают пустым множеством, а не null"));
        done = Set.copyOf(Objects.requireNonNull(
                done, "done не может быть null: сделанных шагов нет — пустое множество, а не null"));
        blocked = Set.copyOf(Objects.requireNonNull(
                blocked, "blocked не может быть null: заблокированных шагов нет — пустое множество, а не null"));
    }
}
