package com.theminespirit.core.board;

import java.util.Objects;
import java.util.Optional;

/**
 * Генератор первых шагов на новом месте: каждое действие — отдельная задача доски,
 * строго по цепочке. Вход — чистые факты о поселении, решение — один следующий кейс.
 */
public final class FirstStepsPlan {
    private FirstStepsPlan() {}

    public static Optional<BoardCase> next(SettlementFacts facts) {
        Objects.requireNonNull(facts, "facts не может быть null: генератор решает по фактам, а не вслепую");
        if (!facts.hasHome()) {
            return Optional.empty();
        }
        if (!facts.groundsCleared()) {
            return Optional.of(BoardCase.CLEAR_GROUNDS);
        }
        if (facts.chests() < 2) {
            return Optional.of(BoardCase.STOCK_CHESTS);
        }
        if (!facts.craftingStation()) {
            return Optional.of(BoardCase.CRAFTING_STATION);
        }
        // Булыжник добывается только в шахте (решение владельца 21.10): деревянный набор → шахта → каменный набор.
        Optional<BoardCase> wooden = ToolSet.nextMissing(facts.tools(), ToolSet.wooden());
        if (wooden.isPresent()) {
            return wooden;
        }
        if (!facts.mineEntrance()) {
            return Optional.of(BoardCase.BUILD_MINE);
        }
        Optional<BoardCase> stone = ToolSet.nextMissing(facts.tools(), ToolSet.stone());
        if (stone.isPresent()) {
            return stone;
        }
        if (!facts.fence()) {
            return Optional.of(BoardCase.BUILD_FENCE);
        }
        if (!facts.lighting()) {
            return Optional.of(BoardCase.LIGHTING);
        }
        if (!facts.houses()) {
            return Optional.of(BoardCase.BUILD_HOUSE);
        }
        if (!facts.smithingTable()) {
            return Optional.of(BoardCase.CRAFT_SMITHING_TABLE);
        }
        if (!facts.anvil()) {
            return Optional.of(BoardCase.CRAFT_ANVIL);
        }
        return Optional.empty();
    }
}
