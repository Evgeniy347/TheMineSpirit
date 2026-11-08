package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.StageRules;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettlementTreeScenarioTests {
    private static final int PERCENT = 100;

    @Test
    void stageOne_PartlyBuilt_UsesWorldPercentsAndKeepsGateClosed() {
        SettlementTree tree = stageOne(facts().board(BoardCase.CLEAR_GROUNDS, PERCENT)
                .board(BoardCase.BUILD_MINE, 40)
                .board(BoardCase.BUILD_FENCE, PERCENT)
                .board(BoardCase.BUILD_STREETS, PERCENT)
                .board(BoardCase.STOCK_CHESTS, PERCENT)
                .board(BoardCase.LIGHTING, PERCENT)
                .board(BoardCase.BUILD_ORCHARD, 0)
                .board(BoardCase.BUILD_HOUSE, 0)
                .build());

        assertEquals(
                "Создать поселение",
                tree.node("s1:root").orElseThrow().title(),
                "Корень древа этапа 1 — «Создать поселение»: с него начинается развитие");
        assertEquals(
                40,
                tree.node("s1:case:BUILD_MINE:1").orElseThrow().percent(),
                "Шахта 40%: процент берётся из состояния мира, а не из отметки журнала");
        assertEquals(
                SettlementTree.State.IN_PROGRESS,
                tree.node("s1:case:BUILD_MINE:1").orElseThrow().state(),
                "Шахта 40% — в работе, а не готова");
        assertEquals(
                SettlementTree.State.NOT_STARTED,
                tree.node("s1:case:BUILD_ORCHARD:1").orElseThrow().state(),
                "Сад 0% — не начат");
        assertEquals(
                PERCENT,
                tree.node("s1:case:BUILD_FENCE:1").orElseThrow().percent(),
                "Забор 100%: готовность мира доходит до предела");
        SettlementTree.Node gate = tree.node("s1:gate").orElseThrow();
        assertEquals(
                SettlementTree.State.LOCKED,
                gate.state(),
                "Ворота этапа закрыты, пока шахта, сад и дом не готовы целиком");
        assertTrue(
                gate.requirements().contains("Шахта"),
                "Закрытые ворота называют недостающую обязательную постройку «Шахта»: игрок видит, чего ждать");
        assertTrue(
                gate.requirements().contains("Сад"),
                "Закрытые ворота называют недостающий «Сад»: подсказка ведёт к неготовой постройке");
    }

    @Test
    void stageOne_AllMandatoryReady_OpensGateAndIgnoresPlanExtras() {
        SettlementTree tree = stageOne(facts().board(BoardCase.CLEAR_GROUNDS, PERCENT)
                .board(BoardCase.BUILD_MINE, PERCENT)
                .board(BoardCase.BUILD_FENCE, PERCENT)
                .board(BoardCase.BUILD_STREETS, PERCENT)
                .board(BoardCase.STOCK_CHESTS, PERCENT)
                .board(BoardCase.LIGHTING, PERCENT)
                .board(BoardCase.BUILD_ORCHARD, PERCENT)
                .board(BoardCase.BUILD_HOUSE, PERCENT)
                .build());

        assertEquals(
                SettlementTree.State.READY,
                tree.node("s1:gate").orElseThrow().state(),
                "Все обязательные постройки этапа 1 готовы — ворота этапа 2 открыты");
        SettlementTree.Node wheat = tree.node("s1:plan:wheat_field:1").orElseThrow();
        assertFalse(
                wheat.blocking(),
                "Грядка пшеницы из плана этапа 1 не держит переход: ворота открывает набор stages.json buildings");
        assertEquals(
                SettlementTree.State.NOT_STARTED,
                wheat.state(),
                "Грядка пшеницы без постройки показана серой и не мешает открыть этап");
    }

    @Test
    void upgrades_NextLevelFactsLocked_ShowGrayUntilPreviousLevelReady() {
        SettlementTree tree = stageTwo(facts().plan("family_house_a", 1, PERCENT)
                .plan("family_house_a", 2, 50)
                .build());

        SettlementTree.Node first = tree.node("s2:plan:family_house_a:1").orElseThrow();
        assertEquals(
                SettlementTree.State.READY, first.state(), "Первый уровень дома семьи готов — ветка улучшений открыта");
        SettlementTree.Node second = tree.node("s2:plan:family_house_a:2").orElseThrow();
        assertEquals(
                SettlementTree.State.IN_PROGRESS, second.state(), "Второй уровень дома семьи строится: 50% в работе");
        assertEquals(50, second.percent(), "Второй уровень показывает 50% из аудита мира");
        SettlementTree.Node third = tree.node("s2:plan:family_house_a:3").orElseThrow();
        assertEquals(
                SettlementTree.State.LOCKED, third.state(), "Третий уровень закрыт, пока второй не построен целиком");
        assertEquals(
                0, third.percent(), "Закрытый третий уровень не показывает процент: сначала нужен предыдущий уровень");
        assertTrue(
                third.requirements().contains("Дом семьи A (уровень 2)"),
                "Подсказка третьего уровня называет предшественника «Дом семьи A (уровень 2)»");
    }

    @Test
    void upgrades_PreviousLevelMissing_LocksNextEvenWithWorldProgress() {
        SettlementTree tree = stageTwo(facts().plan("family_house_a", 1, 30)
                .plan("family_house_a", 2, 70)
                .build());

        assertEquals(
                SettlementTree.State.IN_PROGRESS,
                tree.node("s2:plan:family_house_a:1").orElseThrow().state(),
                "Первый уровень дома семьи в работе: 30%");
        assertEquals(
                SettlementTree.State.LOCKED,
                tree.node("s2:plan:family_house_a:2").orElseThrow().state(),
                "Второй уровень закрыт, пока первый не готов, даже если в мире уже стоит 70% его схемы");
        assertEquals(
                0,
                tree.node("s2:plan:family_house_a:2").orElseThrow().percent(),
                "Закрытый второй уровень не показывает 70%: игрок видит недоступность, а не ложный прогресс");
    }

    @Test
    void upgrades_PreviousLevelReady_ShowsNextNotStarted() {
        SettlementTree tree = stageTwo(facts().plan("family_house_a", 1, PERCENT)
                .plan("family_house_a", 2, PERCENT)
                .build());

        assertEquals(
                SettlementTree.State.READY,
                tree.node("s2:plan:family_house_a:2").orElseThrow().state(),
                "Второй уровень готов — следующий шаг ветки становится доступным");
        assertEquals(
                SettlementTree.State.NOT_STARTED,
                tree.node("s2:plan:family_house_a:3").orElseThrow().state(),
                "Третий уровень после готового второго — не начат, а не закрыт");
    }

    @Test
    void unknownWorld_UnloadedChunk_IsNotReady() {
        SettlementTree tree = stageOne(facts().unknown(BoardCase.BUILD_MINE).build());

        SettlementTree.Node mine = tree.node("s1:case:BUILD_MINE:1").orElseThrow();
        assertEquals(
                SettlementTree.State.UNKNOWN,
                mine.state(),
                "Незагруженный чанк шахты — «мир не проверен», а не «готово»");
        assertEquals(0, mine.percent(), "Неизвестная постройка не показывает готовность, которой мир не подтвердил");
        assertEquals(
                SettlementTree.State.LOCKED,
                tree.node("s1:gate").orElseThrow().state(),
                "Ворота этапа закрыты, пока мир не подтвердил обязательную постройку");
    }

    @Test
    void percentFromWorld_PartialProgress_BeatsJournalReadyFlag() {
        SettlementTreeFacts facts = facts().board(BoardCase.BUILD_MINE, 40).build();
        SettlementTree tree = stageOne(facts);

        assertEquals(
                40,
                tree.node("s1:case:BUILD_MINE:1").orElseThrow().percent(),
                "Процент берётся из состояния мира (40%), а не из готовности журнала: иначе подлог «по журналу» "
                        + "показывал бы 100%");
    }

    private static SettlementTree stageOne(SettlementTreeFacts facts) {
        return SettlementTrees.build(StageRules.load(), facts, SettlementTreeIcons.load(), 1, 1);
    }

    private static SettlementTree stageTwo(SettlementTreeFacts facts) {
        return SettlementTrees.build(StageRules.load(), facts, SettlementTreeIcons.load(), 2, 2);
    }

    private static FactsBuilder facts() {
        return new FactsBuilder();
    }

    private static final class FactsBuilder {
        private final Map<String, SettlementTreeFacts.Fact> facts = new LinkedHashMap<>();

        FactsBuilder board(BoardCase boardCase, int percent) {
            return board(boardCase, percent, percent >= PERCENT);
        }

        FactsBuilder board(BoardCase boardCase, int percent, boolean ready) {
            facts.put(
                    SettlementTreeFacts.boardKey(boardCase, 1),
                    new SettlementTreeFacts.Fact(percent, ready, false, Map.of(), null));
            return this;
        }

        FactsBuilder unknown(BoardCase boardCase) {
            facts.put(
                    SettlementTreeFacts.boardKey(boardCase, 1),
                    new SettlementTreeFacts.Fact(0, false, true, Map.of(), null));
            return this;
        }

        FactsBuilder plan(String planId, int level, int percent) {
            facts.put(
                    SettlementTreeFacts.planKey(planId, level),
                    new SettlementTreeFacts.Fact(percent, percent >= PERCENT, false, Map.of(), null));
            return this;
        }

        SettlementTreeFacts build() {
            return new SettlementTreeFacts(facts);
        }
    }
}
