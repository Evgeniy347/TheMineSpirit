package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Генератор первых шагов на новом месте: каждое действие — отдельная задача доски, строго по цепочке. Вход — чистые
 * факты о поселении, решение — один следующий кейс.
 *
 * <p>Порядок — решение владельца 21.10: верстак → деревянные инструменты → шахта (вход и спуск) →
 * каменные инструменты (булыжник со спуска) → печь → свет (факелы) → сундуки → расчистка участка → забор → дом →
 * стол кузнеца → наковальня. Шаг, сделанный по журналу доски (кроме построек раскладки — их сверяет мир), больше
 * не повторяется, даже если факт мира не виден или регрессировал (шахта без лестницы, сломанный инструмент — его
 * восполняет постоянный запас); шаг, упавший до BLOCKED, пропускается — дух получает следующий шаг, а не стоит (правило владельца).
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
            if (facts.blocked().contains(boardCase) || step.finished(facts)) {
                continue;
            }
            return Optional.of(boardCase);
        }
        return Optional.empty();
    }

    // Ближайший невыполненный шаг из wanted после current: его материалы добываются, пока current ещё идёт.
    public static Optional<BoardCase> nextAfter(SettlementFacts facts, BoardCase current, Set<BoardCase> wanted) {
        Objects.requireNonNull(facts, "Факты поселения не могут быть null: по ним видно, что уже сделано");
        Objects.requireNonNull(wanted, "Набор шагов не может быть null: пустой задают пустым набором");
        boolean passed = false;
        for (Step step : ORDER) {
            BoardCase boardCase = step.boardCase();
            if (!passed) {
                passed = boardCase == current;
                continue;
            }
            boolean pending = !facts.blocked().contains(boardCase) && !step.finished(facts);
            if (pending && wanted.contains(boardCase)) {
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
        // Выравнивание — свой факт мира, а не «нет деревьев»: на голом неровном участке расчистка с выравниванием
        // пропускалась навсегда (INC-50).
        steps.add(new Step(BoardCase.CLEAR_GROUNDS, facts -> facts.groundsCleared() && facts.plotLevel()));
        // Свет, ограда и дом — постройки раскладки участка (SettlementLayout): готовы, только когда задание закрыто
        // в журнале И постройка стоит в мире по своим клеткам. Любой факел рядом её не заменяет (ограда 93 из 105
        // считалась готовой, приёмка 23.10), а DONE старой сборки при пустом мире не вечен (INC-50).
        steps.add(Step.layout(BoardCase.LIGHTING, SettlementFacts::lighting));
        steps.add(Step.layout(BoardCase.BUILD_FENCE, SettlementFacts::fence));
        steps.add(Step.layout(BoardCase.BUILD_HOUSE, SettlementFacts::houses));
        steps.add(new Step(BoardCase.CRAFT_SMITHING_TABLE, SettlementFacts::smithingTable));
        steps.add(new Step(BoardCase.CRAFT_ANVIL, SettlementFacts::anvil));
        return List.copyOf(steps);
    }

    private record Step(BoardCase boardCase, Predicate<SettlementFacts> satisfied, boolean layout) {
        Step(BoardCase boardCase, Predicate<SettlementFacts> satisfied) {
            this(boardCase, satisfied, false);
        }

        static Step layout(BoardCase boardCase, Predicate<SettlementFacts> built) {
            return new Step(boardCase, built, true);
        }

        // Обычный шаг сделан по журналу или по факту мира; постройка раскладки — только по обоим сразу.
        boolean finished(SettlementFacts facts) {
            boolean journal = facts.done().contains(boardCase);
            return layout ? journal && satisfied.test(facts) : journal || satisfied.test(facts);
        }
    }
}
