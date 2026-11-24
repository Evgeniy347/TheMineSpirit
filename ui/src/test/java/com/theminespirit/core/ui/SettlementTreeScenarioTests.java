package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.StageRules;
import com.theminespirit.core.board.api.BoardCase;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettlementTreeScenarioTests {
    private static final int PERCENT = 100;

    @Test
    void stageOne_PartlyBuilt_UsesWorldPercentsAndKeepsGateClosed() {
        SettlementTree tree = stageOne(facts().board(BoardCase.CLEAR_GROUNDS, PERCENT)
                .board(BoardCase.BUILD_MINE, 40)
                .board(BoardCase.BUILD_STREETS, PERCENT)
                .board(BoardCase.STOCK_CHESTS, PERCENT)
                .board(BoardCase.LIGHTING, PERCENT)
                .board(BoardCase.BUILD_ORCHARD, 0)
                .board(BoardCase.BUILD_HOUSE, 0)
                .build());

        assertEquals(
                "Создать поселение",
                tree.node("s1:root").orElseThrow().title(),
                "Корень древа этапа 1 — «Создать поселение»: с него начинается развитие Иначе тест stageOne_PartlyBuilt_UsesWorldPercentsAndKeepsGateClosed перестанет ловить эту поломку. При смене stageOne_PartlyBuilt_UsesWorldPercentsAndKeepsGateClosed проверь это утверждение.");
        assertEquals(
                40,
                tree.node("s1:case:BUILD_MINE:1").orElseThrow().percent(),
                "Шахта 40%: процент берётся из состояния мира, а не из отметки журнала Иначе тест stageOne_PartlyBuilt_UsesWorldPercentsAndKeepsGateClosed перестанет ловить эту поломку. При смене stageOne_PartlyBuilt_UsesWorldPercentsAndKeepsGateClosed проверь это утверждение.");
        assertEquals(
                SettlementTree.State.IN_PROGRESS,
                tree.node("s1:case:BUILD_MINE:1").orElseThrow().state(),
                "Шахта 40% — в работе, а не готова Иначе тест stageOne_PartlyBuilt_UsesWorldPercentsAndKeepsGateClosed перестанет ловить эту поломку. При смене stageOne_PartlyBuilt_UsesWorldPercentsAndKeepsGateClosed проверь это утверждение.");
        assertEquals(
                SettlementTree.State.NOT_STARTED,
                tree.node("s1:case:BUILD_ORCHARD:1").orElseThrow().state(),
                "Сад 0% — не начат Иначе тест stageOne_PartlyBuilt_UsesWorldPercentsAndKeepsGateClosed перестанет ловить эту поломку. При смене stageOne_PartlyBuilt_UsesWorldPercentsAndKeepsGateClosed проверь это утверждение.");
        SettlementTree.Node gate = tree.node("s1:gate").orElseThrow();
        assertEquals(
                SettlementTree.State.LOCKED,
                gate.state(),
                "Ворота этапа закрыты, пока шахта, сад и дом не готовы целиком Иначе тест stageOne_PartlyBuilt_UsesWorldPercentsAndKeepsGateClosed перестанет ловить эту поломку. При смене stageOne_PartlyBuilt_UsesWorldPercentsAndKeepsGateClosed проверь это утверждение.");
        assertTrue(
                gate.requirements().contains("Шахта"),
                "Закрытые ворота называют недостающую обязательную постройку «Шахта»: игрок видит, чего ждать Иначе тест stageOne_PartlyBuilt_UsesWorldPercentsAndKeepsGateClosed перестанет ловить эту поломку. При смене stageOne_PartlyBuilt_UsesWorldPercentsAndKeepsGateClosed проверь это утверждение.");
        assertTrue(
                gate.requirements().contains("Сад"),
                "Закрытые ворота называют недостающий «Сад»: подсказка ведёт к неготовой постройке Иначе тест stageOne_PartlyBuilt_UsesWorldPercentsAndKeepsGateClosed перестанет ловить эту поломку. При смене stageOne_PartlyBuilt_UsesWorldPercentsAndKeepsGateClosed проверь это утверждение.");
    }

    @Test
    void stageOne_AllMandatoryReady_OpensGateAndIgnoresPlanExtras() {
        SettlementTree tree = stageOne(facts().board(BoardCase.CLEAR_GROUNDS, PERCENT)
                .board(BoardCase.BUILD_MINE, PERCENT)
                .board(BoardCase.BUILD_STREETS, PERCENT)
                .board(BoardCase.STOCK_CHESTS, PERCENT)
                .board(BoardCase.LIGHTING, PERCENT)
                .board(BoardCase.BUILD_ORCHARD, PERCENT)
                .board(BoardCase.BUILD_HOUSE, PERCENT)
                .plan("storage", 1, PERCENT)
                .plan("storage_2", 1, PERCENT)
                .plan("storage_3", 1, PERCENT)
                .build());

        assertEquals(
                SettlementTree.State.READY,
                tree.node("s1:gate").orElseThrow().state(),
                "Все обязательные постройки этапа 1 готовы — ворота этапа 2 открыты Иначе тест stageOne_AllMandatoryReady_OpensGateAndIgnoresPlanExtras перестанет ловить эту поломку. При смене stageOne_AllMandatoryReady_OpensGateAndIgnoresPlanExtras проверь это утверждение.");
        SettlementTree.Node wheat = tree.node("s1:plan:wheat_field:1").orElseThrow();
        assertFalse(
                wheat.blocking(),
                "Грядка пшеницы из плана этапа 1 не держит переход: ворота открывает набор stages.json buildings Иначе тест stageOne_AllMandatoryReady_OpensGateAndIgnoresPlanExtras перестанет ловить эту поломку. При смене stageOne_AllMandatoryReady_OpensGateAndIgnoresPlanExtras проверь это утверждение.");
        assertEquals(
                SettlementTree.State.NOT_STARTED,
                wheat.state(),
                "Грядка пшеницы без постройки показана серой и не мешает открыть этап Иначе тест stageOne_AllMandatoryReady_OpensGateAndIgnoresPlanExtras перестанет ловить эту поломку. При смене stageOne_AllMandatoryReady_OpensGateAndIgnoresPlanExtras проверь это утверждение.");
    }

    @Test
    void upgrades_NextLevelFactsLocked_ShowGrayUntilPreviousLevelReady() {
        SettlementTree tree = stageTwo(facts().plan("family_house_a", 1, PERCENT)
                .plan("family_house_a", 2, 50)
                .build());

        SettlementTree.Node first = tree.node("s2:plan:family_house_a:1").orElseThrow();
        assertEquals(
                SettlementTree.State.READY,
                first.state(),
                "Первый уровень дома семьи готов — ветка улучшений открыта Иначе тест upgrades_NextLevelFactsLocked_ShowGrayUntilPreviousLevelReady перестанет ловить эту поломку. При смене upgrades_NextLevelFactsLocked_ShowGrayUntilPreviousLevelReady проверь это утверждение.");
        SettlementTree.Node second = tree.node("s2:plan:family_house_a:2").orElseThrow();
        assertEquals(
                SettlementTree.State.IN_PROGRESS,
                second.state(),
                "Второй уровень дома семьи строится: 50% в работе Иначе тест upgrades_NextLevelFactsLocked_ShowGrayUntilPreviousLevelReady перестанет ловить эту поломку. При смене upgrades_NextLevelFactsLocked_ShowGrayUntilPreviousLevelReady проверь это утверждение.");
        assertEquals(
                50,
                second.percent(),
                "Второй уровень показывает 50% из аудита мира Иначе тест upgrades_NextLevelFactsLocked_ShowGrayUntilPreviousLevelReady перестанет ловить эту поломку. При смене upgrades_NextLevelFactsLocked_ShowGrayUntilPreviousLevelReady проверь это утверждение.");
        SettlementTree.Node third = tree.node("s2:plan:family_house_a:3").orElseThrow();
        assertEquals(
                SettlementTree.State.LOCKED,
                third.state(),
                "Третий уровень закрыт, пока второй не построен целиком Иначе тест upgrades_NextLevelFactsLocked_ShowGrayUntilPreviousLevelReady перестанет ловить эту поломку. При смене upgrades_NextLevelFactsLocked_ShowGrayUntilPreviousLevelReady проверь это утверждение.");
        assertEquals(
                0,
                third.percent(),
                "Закрытый третий уровень не показывает процент: сначала нужен предыдущий уровень Иначе тест upgrades_NextLevelFactsLocked_ShowGrayUntilPreviousLevelReady перестанет ловить эту поломку. При смене upgrades_NextLevelFactsLocked_ShowGrayUntilPreviousLevelReady проверь это утверждение.");
        assertTrue(
                third.requirements().contains("Дом семьи A (уровень 2)"),
                "Подсказка третьего уровня называет предшественника «Дом семьи A (уровень 2)» Иначе тест upgrades_NextLevelFactsLocked_ShowGrayUntilPreviousLevelReady перестанет ловить эту поломку. При смене upgrades_NextLevelFactsLocked_ShowGrayUntilPreviousLevelReady проверь это утверждение.");
    }

    @Test
    void upgrades_PreviousLevelMissing_LocksNextEvenWithWorldProgress() {
        SettlementTree tree = stageTwo(facts().plan("family_house_a", 1, 30)
                .plan("family_house_a", 2, 70)
                .build());

        assertEquals(
                SettlementTree.State.IN_PROGRESS,
                tree.node("s2:plan:family_house_a:1").orElseThrow().state(),
                "Первый уровень дома семьи в работе: 30% Иначе тест upgrades_PreviousLevelMissing_LocksNextEvenWithWorldProgress перестанет ловить эту поломку. При смене upgrades_PreviousLevelMissing_LocksNextEvenWithWorldProgress проверь это утверждение.");
        assertEquals(
                SettlementTree.State.LOCKED,
                tree.node("s2:plan:family_house_a:2").orElseThrow().state(),
                "Второй уровень закрыт, пока первый не готов, даже если в мире уже стоит 70% его схемы Иначе тест upgrades_PreviousLevelMissing_LocksNextEvenWithWorldProgress перестанет ловить эту поломку. При смене upgrades_PreviousLevelMissing_LocksNextEvenWithWorldProgress проверь это утверждение.");
        assertEquals(
                0,
                tree.node("s2:plan:family_house_a:2").orElseThrow().percent(),
                "Закрытый второй уровень не показывает 70%: игрок видит недоступность, а не ложный прогресс Иначе тест upgrades_PreviousLevelMissing_LocksNextEvenWithWorldProgress перестанет ловить эту поломку. При смене upgrades_PreviousLevelMissing_LocksNextEvenWithWorldProgress проверь это утверждение.");
    }

    @Test
    void upgrades_PreviousLevelReady_ShowsNextNotStarted() {
        SettlementTree tree = stageTwo(facts().plan("family_house_a", 1, PERCENT)
                .plan("family_house_a", 2, PERCENT)
                .build());

        assertEquals(
                SettlementTree.State.READY,
                tree.node("s2:plan:family_house_a:2").orElseThrow().state(),
                "Второй уровень готов — следующий шаг ветки становится доступным Иначе тест upgrades_PreviousLevelReady_ShowsNextNotStarted перестанет ловить эту поломку. При смене upgrades_PreviousLevelReady_ShowsNextNotStarted проверь это утверждение.");
        assertEquals(
                SettlementTree.State.NOT_STARTED,
                tree.node("s2:plan:family_house_a:3").orElseThrow().state(),
                "Третий уровень после готового второго — не начат, а не закрыт Иначе тест upgrades_PreviousLevelReady_ShowsNextNotStarted перестанет ловить эту поломку. При смене upgrades_PreviousLevelReady_ShowsNextNotStarted проверь это утверждение.");
    }

    @Test
    void unknownWorld_UnloadedChunk_IsNotReady() {
        SettlementTree tree = stageOne(facts().unknown(BoardCase.BUILD_MINE).build());

        SettlementTree.Node mine = tree.node("s1:case:BUILD_MINE:1").orElseThrow();
        assertEquals(
                SettlementTree.State.UNKNOWN,
                mine.state(),
                "Незагруженный чанк шахты — «мир не проверен», а не «готово» Иначе тест unknownWorld_UnloadedChunk_IsNotReady перестанет ловить эту поломку. При смене unknownWorld_UnloadedChunk_IsNotReady проверь это утверждение.");
        assertEquals(
                0,
                mine.percent(),
                "Неизвестная постройка не показывает готовность, которой мир не подтвердил Иначе тест unknownWorld_UnloadedChunk_IsNotReady перестанет ловить эту поломку. При смене unknownWorld_UnloadedChunk_IsNotReady проверь это утверждение.");
        assertEquals(
                SettlementTree.State.LOCKED,
                tree.node("s1:gate").orElseThrow().state(),
                "Ворота этапа закрыты, пока мир не подтвердил обязательную постройку Иначе тест unknownWorld_UnloadedChunk_IsNotReady перестанет ловить эту поломку. При смене unknownWorld_UnloadedChunk_IsNotReady проверь это утверждение.");
    }

    @Test
    void stageOne_ClearingIsSeparateItem_WithDefectCounts() {
        SettlementTree tree = stageOne(facts().board(BoardCase.CLEAR_GROUNDS, 70, false, "ям 3, бугров 2, столбов 1")
                .board(BoardCase.BUILD_STREETS, PERCENT)
                .build());

        SettlementTree.Node clearing = tree.node("s1:case:CLEAR_GROUNDS:1").orElseThrow();
        SettlementTree.Node streets = tree.node("s1:case:BUILD_STREETS:1").orElseThrow();

        assertEquals(
                SettlementTree.State.IN_PROGRESS,
                clearing.state(),
                "Неровная зона — работа в процессе, а не готово: этап не закрывается при дефектах Иначе тест stageOne_ClearingIsSeparateItem_WithDefectCounts перестанет ловить эту поломку. При смене stageOne_ClearingIsSeparateItem_WithDefectCounts проверь это утверждение.");
        assertEquals(
                "ям 3, бугров 2, столбов 1",
                clearing.detail(),
                "Вкладка J называет числа дефектов ровности: игрок видит, что именно чинят Иначе тест stageOne_ClearingIsSeparateItem_WithDefectCounts перестанет ловить эту поломку. При смене stageOne_ClearingIsSeparateItem_WithDefectCounts проверь это утверждение.");
        assertTrue(
                !clearing.id().equals(streets.id()),
                "Расчистка и улицы — отдельные пункты древа: улицы не прячут дефекты зоны Иначе тест stageOne_ClearingIsSeparateItem_WithDefectCounts перестанет ловить эту поломку. При смене stageOne_ClearingIsSeparateItem_WithDefectCounts проверь это утверждение.");
        assertEquals(
                SettlementTree.State.READY,
                streets.state(),
                "Улицы готовы независимо от дефектов ровности Иначе тест stageOne_ClearingIsSeparateItem_WithDefectCounts перестанет ловить эту поломку. При смене stageOne_ClearingIsSeparateItem_WithDefectCounts проверь это утверждение.");
    }

    @Test
    void stageOne_CleanClearing_ShowsZeroDefects() {
        SettlementTree tree =
                stageOne(facts().board(BoardCase.CLEAR_GROUNDS, PERCENT, true, "ям 0, бугров 0, столбов 0")
                        .build());

        SettlementTree.Node clearing = tree.node("s1:case:CLEAR_GROUNDS:1").orElseThrow();
        assertEquals(
                SettlementTree.State.READY,
                clearing.state(),
                "Ровная зона закрывает расчистку Иначе тест stageOne_CleanClearing_ShowsZeroDefects перестанет ловить эту поломку. При смене stageOne_CleanClearing_ShowsZeroDefects проверь это утверждение.");
        assertEquals(
                "ям 0, бугров 0, столбов 0",
                clearing.detail(),
                "Ноль дефектов показывается числом, а не пусто Иначе тест stageOne_CleanClearing_ShowsZeroDefects перестанет ловить эту поломку. При смене stageOne_CleanClearing_ShowsZeroDefects проверь это утверждение.");
    }

    @Test
    void percentFromWorld_PartialProgress_BeatsJournalReadyFlag() {
        SettlementTreeFacts facts = facts().board(BoardCase.BUILD_MINE, 40).build();
        SettlementTree tree = stageOne(facts);

        assertEquals(
                40,
                tree.node("s1:case:BUILD_MINE:1").orElseThrow().percent(),
                "Процент берётся из состояния мира (40%), а не из готовности журнала: иначе подлог «по журналу» "
                        + "показывал бы 100% При смене percentFromWorld_PartialProgress_BeatsJournalReadyFlag проверь это утверждение.");
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
            return board(boardCase, percent, ready, "");
        }

        FactsBuilder board(BoardCase boardCase, int percent, boolean ready, String detail) {
            facts.put(
                    SettlementTreeFacts.boardKey(boardCase, 1),
                    new SettlementTreeFacts.Fact(percent, ready, false, Map.of(), null, detail));
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
