package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class BoardRulesTests {

    @Test
    void repairFence_Higher_ThanCrater() {
        assertTrue(
                BoardRules.priorityOf(BoardCase.REPAIR_FENCE) > BoardRules.priorityOf(BoardCase.FILL_CRATER),
                "Ремонт ограждений обязан быть выше воронки: ограждения — первый приоритет "
                        + "из источника (п. 4), иначе дух пойдёт засыпать яму, пока забор открыт");
    }

    @Test
    void crater_Lower_ThanSupply() {
        assertTrue(
                BoardRules.priorityOf(BoardCase.FILL_CRATER) < BoardRules.priorityOf(BoardCase.SUPPLY_SITE),
                "Воронка обязана быть ниже снабжения (v2: «воронка — задание с низким приоритетом»): "
                        + "открытая яма ждёт, пока поселение снабжается, иначе снабжение встанет");
    }

    @Test
    void crater_Higher_ThanShelves() {
        assertTrue(
                BoardRules.priorityOf(BoardCase.FILL_CRATER) > BoardRules.priorityOf(BoardCase.ENCHANT_SHELVES),
                "Воронка обязана быть выше полок чаровальни: полки — самый низкий приоритет, " + "яма важнее книг");
    }

    @Test
    void supply_Lower_ThanFirstSteps() {
        assertTrue(
                BoardRules.priorityOf(BoardCase.SUPPLY_SITE) < BoardRules.priorityOf(BoardCase.CLEAR_GROUNDS),
                "Снабжение обязано быть ниже первых шагов: сначала зачистка территории, "
                        + "потом воз материалов, иначе дух таскает ресурсы в лес");
    }

    @Test
    void everyCase_Has_Priority() {
        for (BoardCase boardCase : BoardCase.values()) {
            int priority = BoardRules.priorityOf(boardCase);

            assertTrue(
                    priority >= 0 && priority <= BoardTuning.MAX_PRIORITY,
                    "Приоритет кейса " + boardCase + " равен " + priority
                            + " и выходит за [0, " + BoardTuning.MAX_PRIORITY
                            + "]: кейс-сирота не получил строку таблицы, а молчаливый "
                            + "пропуск спрятал бы задание в очереди");
        }
    }

    @Test
    void priorityOf_Is_Deterministic() {
        assertEquals(
                BoardRules.priorityOf(BoardCase.REPAIR_FENCE),
                BoardRules.priorityOf(BoardCase.REPAIR_FENCE),
                "Приоритет кейса обязан быть детерминирован: доска сортирует очередь "
                        + "по приоритету, нестабильное значение перетасует выдачу между пересборками");
    }

    @Test
    void priorityOf_NullCase_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> BoardRules.priorityOf(null),
                "Кейс вне таблицы обязан падать с текстом: сирота в очереди проваливается "
                        + "молча, и задание никогда не выдаётся без объяснения причины");
        assertTrue(
                error.getMessage().contains("Приоритет кейса не задан"),
                "Сообщение обязано называть причину: приоритет не задан. Фактическое: " + error.getMessage());
    }

    @Property
    void priorityOrder_Agrees_WithPartialOrder(
            @ForAll("boardCases") BoardCase first, @ForAll("boardCases") BoardCase second) {
        if (groupRank(first) >= groupRank(second)) {
            return;
        }
        assertTrue(
                BoardRules.priorityOf(first) > BoardRules.priorityOf(second),
                "Порядок приоритетов обязан согласовываться с частичным порядком v2 "
                        + "«ремонт > еда > животные, пополнение еды и факелы > стройки, перенос, "
                        + "разведение и контроль > снабжение > воронка > полки»: " + first + " ("
                        + BoardRules.priorityOf(first) + ") ниже или равен " + second + " ("
                        + BoardRules.priorityOf(second)
                        + "), и доска выдаст сначала менее важное");
    }

    @Property
    void priorityOf_Is_StableAcrossReads() {
        for (BoardCase boardCase : BoardCase.values()) {
            assertEquals(
                    BoardRules.priorityOf(boardCase),
                    BoardRules.priorityOf(boardCase),
                    "Приоритет кейса " + boardCase + " обязан читаться одинаково: по нему "
                            + "доска сортирует очередь выдачи");
        }
    }

    private static int groupRank(BoardCase boardCase) {
        if (boardCase == BoardCase.REPAIR_FENCE
                || boardCase == BoardCase.REPAIR_BUILDING
                || boardCase == BoardCase.REPAIR_TOOL) {
            return 0;
        }
        if (boardCase == BoardCase.EAT_TO_FULL) {
            return 1;
        }
        if (boardCase == BoardCase.CAPTURE_ANIMAL
                || boardCase == BoardCase.BUILD_PEN
                || boardCase == BoardCase.LURE_ANIMAL
                || boardCase == BoardCase.DISMANTLE_SHELTER
                || boardCase == BoardCase.RESTOCK_FOOD
                || boardCase == BoardCase.CRAFT_TORCHES
                || boardCase == BoardCase.SMELT_CHARCOAL) {
            return 2;
        }
        if (boardCase == BoardCase.SUPPLY_SITE
                || boardCase == BoardCase.CRAFTING_STATION
                || boardCase == BoardCase.CRAFT_FURNACE
                || boardCase == BoardCase.STOCK_CHESTS
                || boardCase == BoardCase.TRANSFER_ITEM
                || boardCase == BoardCase.MINE_LEFT_ORE
                || boardCase == BoardCase.GATHER_MATERIALS
                || ToolSet.isTool(boardCase)
                || boardCase == BoardCase.CRAFT_SHEARS
                || boardCase == BoardCase.CRAFT_BED
                || boardCase == BoardCase.WEAVE_WOOL) {
            return 4;
        }
        if (boardCase == BoardCase.FILL_CRATER) {
            return 5;
        }
        if (boardCase == BoardCase.ENCHANT_SHELVES || boardCase == BoardCase.HARVEST_GROVE) {
            return 6;
        }
        return 3;
    }

    @Provide
    Arbitrary<BoardCase> boardCases() {
        return Arbitraries.of(Arrays.stream(BoardCase.values())
                .filter(boardCase -> !BoardActivities.isActivity(boardCase))
                .toList());
    }
}
