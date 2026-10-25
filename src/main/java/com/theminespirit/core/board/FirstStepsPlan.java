package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Генератор первых шагов на новом месте: каждое действие — отдельная задача доски; до выравнивания — строго по
 * цепочке, после него — параллельно ({@link #ready}). Вход — чистые
 * факты о поселении, решение — один следующий кейс.
 *
 * <p>Порядок — решение владельца 21.10, уточнён 24.10 (INC-63): временный верстак → деревянные инструменты →
 * расчистка и выравнивание → двор (сундуки, постоянный верстак) → шахта → каменные инструменты → печь → свет →
 * забор → дом → стол кузнеца → наковальня. Шаг, сделанный по журналу доски (кроме построек раскладки — их сверяет мир), больше
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

    // Шаги, которые можно открыть сейчас. До выравнивания включительно — строго по одному, по цепочке; после него
    // все невыполненные шаги открываются сразу на разных духов (решение владельца 25.10): шахта, двор, свет, ограда,
    // хижина, каменные инструменты и печь — тому, кто свободен, а не ждут всей цепочки (сцена 56: после
    // выравнивания работал один дух, двое стояли). Первый в списке — шаг цепочки, как у next.
    public static List<BoardCase> ready(SettlementFacts facts) {
        Objects.requireNonNull(facts, "facts не может быть null: генератор решает по фактам, а не вслепую");
        Optional<BoardCase> first = next(facts);
        if (first.isEmpty()) {
            return List.of();
        }
        int leveling = chain().indexOf(BoardCase.CLEAR_GROUNDS);
        if (chain().indexOf(first.orElseThrow()) <= leveling) {
            return List.of(first.orElseThrow());
        }
        // Стол кузнеца и наковальня (железо) — после дома по цепочке: их добыча железа отнимала духов у этапа 1.
        int house = chain().indexOf(BoardCase.BUILD_HOUSE);
        if (chain().indexOf(first.orElseThrow()) > house) {
            return List.of(first.orElseThrow());
        }
        List<BoardCase> open = new ArrayList<>();
        for (Step step : ORDER) {
            int index = chain().indexOf(step.boardCase());
            if (index > leveling
                    && index <= house
                    && !facts.blocked().contains(step.boardCase())
                    && !step.finished(facts)) {
                open.add(step.boardCase());
            }
        }
        return List.copyOf(open);
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
        // Выравнивание — свой факт мира, а не «нет деревьев» (INC-50), и до двора и факелов.
        // Порядок владельца (INC-63): временный верстак → деревянные инструменты → выравнивание → двор (сундуки,
        // постоянный верстак) на высоте плана → шахта → … Двор до выравнивания вставал в яме на 4 ниже плана, все
        // духи «застревали» в одной точке.
        steps.add(new Step(BoardCase.CRAFTING_STATION, SettlementFacts::craftingStation));
        for (BoardCase tool : ToolSet.wooden()) {
            steps.add(new Step(tool, facts -> ToolSet.covered(tool, facts.tools())));
        }
        steps.add(new Step(BoardCase.CLEAR_GROUNDS, SettlementFacts::groundsCleared, SettlementFacts::plotLevel));
        steps.add(new Step(BoardCase.STOCK_CHESTS, facts -> facts.chests() >= 2));
        // Шахта — постройка плана поселения (INC-59): сделана, только если спуск по схеме стоит на своём месте.
        steps.add(Step.layout(BoardCase.BUILD_MINE, SettlementFacts::mineEntrance));
        for (BoardCase tool : ToolSet.stone()) {
            steps.add(new Step(tool, facts -> ToolSet.covered(tool, facts.tools())));
        }
        steps.add(new Step(BoardCase.CRAFT_FURNACE, SettlementFacts::furnace));
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

    // satisfied — факт мира, заменяющий журнал; world — факт мира, без которого шаг не сделан и по журналу.
    private record Step(BoardCase boardCase, Predicate<SettlementFacts> satisfied, Predicate<SettlementFacts> world) {
        Step(BoardCase boardCase, Predicate<SettlementFacts> satisfied) {
            this(boardCase, satisfied, facts -> true);
        }

        // Постройка раскладки: журнал её не заменяет, а мир обязателен.
        static Step layout(BoardCase boardCase, Predicate<SettlementFacts> built) {
            return new Step(boardCase, facts -> false, built);
        }

        boolean finished(SettlementFacts facts) {
            return (facts.done().contains(boardCase) || satisfied.test(facts)) && world.test(facts);
        }
    }
}
