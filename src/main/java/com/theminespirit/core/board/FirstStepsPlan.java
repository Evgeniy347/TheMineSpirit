package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Генератор первых шагов на новом месте: каждое действие — отдельная задача доски, строго по цепочке. Вход — чистые
 * факты о поселении, решение — один следующий кейс.
 *
 * <p>Порядок — решение владельца 21.10: верстак → деревянные инструменты → шахта (вход и спуск) →
 * каменные инструменты (булыжник со спуска) → печь → свет (факелы) → сундуки → расчистка участка → забор → дом →
 * стол кузнеца → наковальня. Шаг, сделанный по журналу доски, больше не повторяется, даже если факт мира не виден
 * или регрессировал (шахта без лестницы, сломанный инструмент — его восполняет постоянный запас); шаг, упавший до
 * BLOCKED, пропускается — дух получает следующий шаг, а не стоит (правило владельца).
 */
public final class FirstStepsPlan {
    private static final List<Step> ORDER = order();

    private FirstStepsPlan() {}

    public static List<BoardCase> chain() {
        return ORDER.stream().map(Step::boardCase).toList();
    }

    public static Optional<BoardCase> next(SettlementFacts facts) {
        Objects.requireNonNull(facts, "facts не может быть null: генератор решает по фактам, а не вслепую");
        if (!facts.hasHome()) {
            return Optional.empty();
        }
        for (Step step : ORDER) {
            BoardCase boardCase = step.boardCase();
            if (facts.done().contains(boardCase) || facts.blocked().contains(boardCase)) {
                continue;
            }
            if (!step.satisfied().test(facts)) {
                return Optional.of(boardCase);
            }
        }
        return Optional.empty();
    }

    private static List<Step> order() {
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(BoardCase.CRAFTING_STATION, SettlementFacts::craftingStation));
        steps.add(new Step(BoardCase.STOCK_CHESTS, facts -> facts.chests() >= 2));
        for (BoardCase tool : ToolSet.wooden()) {
            steps.add(new Step(tool, facts -> ToolSet.covered(tool, facts.tools())));
        }
        steps.add(new Step(BoardCase.BUILD_MINE, SettlementFacts::mineEntrance));
        for (BoardCase tool : ToolSet.stone()) {
            steps.add(new Step(tool, facts -> ToolSet.covered(tool, facts.tools())));
        }
        steps.add(new Step(BoardCase.CRAFT_FURNACE, SettlementFacts::furnace));
        // Расчистка — до факелов: факелы встают по углам участка (SettlementLayout), а до расчистки там деревья
        // («клетка … занята», приёмка «джунгли» 23.10).
        steps.add(new Step(BoardCase.CLEAR_GROUNDS, SettlementFacts::groundsCleared));
        steps.add(new Step(BoardCase.LIGHTING, SettlementFacts::lighting));
        steps.add(new Step(BoardCase.BUILD_FENCE, SettlementFacts::fence));
        steps.add(new Step(BoardCase.BUILD_HOUSE, SettlementFacts::houses));
        steps.add(new Step(BoardCase.CRAFT_SMITHING_TABLE, SettlementFacts::smithingTable));
        steps.add(new Step(BoardCase.CRAFT_ANVIL, SettlementFacts::anvil));
        return List.copyOf(steps);
    }

    private record Step(BoardCase boardCase, Predicate<SettlementFacts> satisfied) {}
}
