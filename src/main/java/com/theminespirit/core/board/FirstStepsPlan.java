package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

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
        int house = chain().indexOf(BoardCase.BUILD_HOUSE);
        for (Step step : ORDER) {
            BoardCase boardCase = step.boardCase();
            if (chain().indexOf(boardCase) > house && !blockedStageOne(facts).isEmpty()) {
                return Optional.empty();
            }
            if (facts.blocked().contains(boardCase) || step.finished(facts)) {
                continue;
            }
            return Optional.of(boardCase);
        }
        return Optional.empty();
    }

    public static List<BoardCase> blockedStageOne(SettlementFacts facts) {
        Objects.requireNonNull(facts, "facts не может быть null: заблокированные шаги читаются по фактам");
        int house = chain().indexOf(BoardCase.BUILD_HOUSE);
        return ORDER.stream()
                .filter(step -> chain().indexOf(step.boardCase()) <= house)
                .filter(step -> facts.blocked().contains(step.boardCase()) && !step.finished(facts))
                .map(Step::boardCase)
                .toList();
    }

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
        for (BoardCase tool : ToolSet.wooden()) {
            steps.add(new Step(tool, facts -> ToolSet.covered(tool, facts.tools())));
        }
        steps.add(new Step(
                BoardCase.CLEAR_GROUNDS,
                SettlementFacts::groundsCleared,
                facts -> facts.plotLevel() || facts.done().contains(BoardCase.CLEAR_GROUNDS)));
        steps.add(new Step(BoardCase.STOCK_CHESTS, facts -> facts.chests() >= 2, facts -> facts.chests() >= 2));
        steps.add(Step.layout(BoardCase.BUILD_MINE, SettlementFacts::mineEntrance));
        for (BoardCase tool : ToolSet.stone()) {
            steps.add(new Step(tool, facts -> ToolSet.covered(tool, facts.tools())));
        }
        steps.add(new Step(BoardCase.CRAFT_FURNACE, SettlementFacts::furnace));
        steps.add(Step.layout(BoardCase.LIGHTING, SettlementFacts::lighting));
        steps.add(Step.layout(BoardCase.BUILD_FENCE, SettlementFacts::fence));
        steps.add(Step.layout(BoardCase.BUILD_HOUSE, SettlementFacts::houses));
        steps.add(new Step(BoardCase.CRAFT_SMITHING_TABLE, SettlementFacts::smithingTable));
        steps.add(new Step(BoardCase.CRAFT_ANVIL, SettlementFacts::anvil));
        steps.add(Step.layout(BoardCase.BUILD_ORCHARD, SettlementFacts::orchard));
        steps.add(Step.layout(BoardCase.BUILD_STREETS, SettlementFacts::streets));
        return List.copyOf(steps);
    }

    private record Step(BoardCase boardCase, Predicate<SettlementFacts> satisfied, Predicate<SettlementFacts> world) {
        Step(BoardCase boardCase, Predicate<SettlementFacts> satisfied) {
            this(boardCase, satisfied, facts -> true);
        }

        static Step layout(BoardCase boardCase, Predicate<SettlementFacts> built) {
            return new Step(boardCase, facts -> false, built);
        }

        boolean finished(SettlementFacts facts) {
            return (facts.done().contains(boardCase) || satisfied.test(facts)) && world.test(facts);
        }
    }
}
