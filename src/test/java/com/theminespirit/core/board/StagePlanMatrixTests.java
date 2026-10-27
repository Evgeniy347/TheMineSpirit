package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class StagePlanMatrixTests {
    private static final List<Set<String>> TOOLS = List.of(
            Set.of(),
            Set.of("wooden_pickaxe", "wooden_axe", "wooden_shovel"),
            Set.of("stone_pickaxe", "stone_axe", "stone_shovel", "stone_sword"));
    private static final int FLAGS = 11;

    private static SettlementFacts facts(int mask, int chests, Set<String> tools, Set<BoardCase> blocked) {
        return new SettlementFacts(
                true,
                bit(mask, 0),
                bit(mask, 0),
                chests,
                bit(mask, 1),
                tools,
                bit(mask, 2),
                bit(mask, 3),
                bit(mask, 4),
                bit(mask, 5),
                bit(mask, 6),
                bit(mask, 7),
                bit(mask, 8),
                bit(mask, 9),
                bit(mask, 10),
                done(mask),
                blocked);
    }

    private static Set<BoardCase> done(int mask) {
        Set<BoardCase> done = EnumSet.noneOf(BoardCase.class);
        BoardCase[] layout = {
            BoardCase.CLEAR_GROUNDS,
            null,
            BoardCase.BUILD_MINE,
            null,
            BoardCase.BUILD_FENCE,
            BoardCase.LIGHTING,
            BoardCase.BUILD_HOUSE,
            BoardCase.BUILD_ORCHARD,
            BoardCase.BUILD_STREETS
        };
        for (int index = 0; index < layout.length; index++) {
            if (layout[index] != null && bit(mask, index)) {
                done.add(layout[index]);
            }
        }
        return done;
    }

    private static boolean bit(int mask, int index) {
        return (mask & (1 << index)) != 0;
    }

    static Stream<Set<BoardCase>> blocks() {
        return Stream.concat(
                Stream.of(Set.<BoardCase>of()), FirstStepsPlan.chain().stream().map(Set::of));
    }

    private static List<SettlementFacts> classes(Set<BoardCase> blocked) {
        List<SettlementFacts> all = new ArrayList<>();
        for (int mask = 0; mask < (1 << FLAGS); mask++) {
            for (int chests : List.of(0, 2)) {
                for (Set<String> tools : TOOLS) {
                    all.add(facts(mask, chests, tools, blocked));
                }
            }
        }
        return all;
    }

    @Tag("mutation")
    @ParameterizedTest
    @MethodSource("blocks")
    void ready_AnyClass_NeverOffersBlockedStep(Set<BoardCase> blocked) {
        List<String> wrong = new ArrayList<>();
        for (SettlementFacts facts : classes(blocked)) {
            for (BoardCase step : FirstStepsPlan.ready(facts)) {
                if (facts.blocked().contains(step)) {
                    wrong.add(step + " при " + facts.blocked());
                }
            }
        }
        assertEquals(List.of(), wrong.subList(0, Math.min(5, wrong.size())), "Заблокированный шаг выдан снова");
    }

    @Tag("mutation")
    @ParameterizedTest
    @MethodSource("blocks")
    void ready_UnblockedStageStepLeft_NeverStalls(Set<BoardCase> blocked) {
        List<String> stalled = new ArrayList<>();
        Set<BoardCase> stage = EnumSet.of(
                BoardCase.CLEAR_GROUNDS,
                BoardCase.STOCK_CHESTS,
                BoardCase.BUILD_MINE,
                BoardCase.LIGHTING,
                BoardCase.BUILD_FENCE,
                BoardCase.BUILD_HOUSE,
                BoardCase.BUILD_ORCHARD,
                BoardCase.BUILD_STREETS);
        for (SettlementFacts facts : classes(blocked)) {
            if (!FirstStepsPlan.ready(facts).isEmpty()) {
                continue;
            }
            for (BoardCase step : stage) {
                if (!facts.blocked().contains(step) && !built(step, facts)) {
                    stalled.add(step + " не построен, заблокировано " + facts.blocked());
                    break;
                }
            }
        }
        assertEquals(
                List.of(),
                stalled.subList(0, Math.min(5, stalled.size())),
                "Этап 1 встал: незаблокированная постройка этапа не построена, а шагов нет (" + stalled.size()
                        + " классов)");
    }

    private static boolean built(BoardCase step, SettlementFacts facts) {
        return switch (step) {
            case CLEAR_GROUNDS -> facts.plotLevel();
            case STOCK_CHESTS -> facts.chests() >= 2;
            case BUILD_MINE -> facts.mineEntrance();
            case LIGHTING -> facts.lighting();
            case BUILD_FENCE -> facts.fence();
            case BUILD_HOUSE -> facts.houses();
            case BUILD_ORCHARD -> facts.orchard();
            case BUILD_STREETS -> facts.streets();
            default -> true;
        };
    }

    @Test
    void ready_StageOneBuilt_OrchardNotBehindAnvil() {
        SettlementFacts facts = facts(0b00001111111, 2, TOOLS.get(2), Set.of());

        assertTrue(
                FirstStepsPlan.ready(facts).contains(BoardCase.BUILD_ORCHARD),
                "Сад — постройка этапа 1 (stages.json), наковальня — нет: сад не ждёт 31 железо на наковальню, "
                        + "получено " + FirstStepsPlan.ready(facts));
    }

    @Test
    void ready_LightingBlocked_OrchardStillOffered() {
        SettlementFacts facts = facts(0b00001011111, 2, TOOLS.get(2), Set.of(BoardCase.LIGHTING));

        assertTrue(
                FirstStepsPlan.ready(facts).contains(BoardCase.BUILD_ORCHARD)
                        || FirstStepsPlan.ready(facts).contains(BoardCase.BUILD_HOUSE),
                "Заблокированный свет (нет угля) не останавливает остальные постройки этапа, получено "
                        + FirstStepsPlan.ready(facts));
    }
}
