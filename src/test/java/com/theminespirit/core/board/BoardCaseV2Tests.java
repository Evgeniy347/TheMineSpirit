package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class BoardCaseV2Tests {

    @Test
    void cases_TotalCount_IsSeventyEight() {
        assertEquals(
                78,
                BoardCase.values().length,
                "Кейсов доски — 78: 63 прежних, укладка улиц, восемь крафтов железного и алмазного инструмента "
                        + "(кирка, топор, лопата, меч), сад этапа 1 и рубка зрелого дерева сада, три подписи тактики "
                        + "(отход, возврат, охрана) и спасение застрявшего духа: набор духа поднимается до уровня из таблицы board. "
                        + "Новый кейс без приоритета, гейта и плана роняет сортировку выдачи");
    }

    @Test
    void cases_Values_AreAlphabetical() {
        List<String> names = new ArrayList<>();
        for (BoardCase boardCase : BoardCase.values()) {
            names.add(boardCase.name());
        }
        List<String> sorted = names.stream().sorted().toList();

        assertEquals(
                sorted,
                names,
                "Константы BoardCase объявлены по алфавиту: обход values() обязан быть упорядочен "
                        + "(детерминизм набора на любой JVM), новый кейс встаёт на своё место, а не в конец");
    }

    @Test
    void cases_EveryCase_HasPriority() {
        for (BoardCase boardCase : BoardCase.values()) {
            int priority = BoardRules.priorityOf(boardCase);

            assertTrue(
                    priority >= 0 && priority <= BoardTuning.MAX_PRIORITY,
                    "Кейс " + boardCase + " обязан иметь базовый приоритет в [0, MAX_PRIORITY]: "
                            + "кейс-сирота роняет сортировку выдачи");
        }
    }

    @Test
    void cases_EveryCase_HasPlanOrIsExternal() {
        for (BoardCase boardCase : BoardCase.values()) {
            if (TicketPlans.EXTERNAL_CASES.contains(boardCase) || BoardActivities.isActivity(boardCase)) {
                continue;
            }
            TaskNode plan = TicketPlans.plan(boardCase, target());

            assertTrue(
                    plan != null,
                    "Кейс " + boardCase + " обязан иметь план или быть названным во внешнем списке "
                            + "TicketPlans.EXTERNAL_CASES: выдача задания без плана оставляет духа без задачи");
        }
        assertTrue(
                TicketPlans.EXTERNAL_CASES.contains(BoardCase.EAT_TO_FULL),
                "EAT_TO_FULL — внешний кейс без плана: дух ест сам скриптом eat из личного инвентаря, "
                        + "план доски ему не нужен");
    }

    @Property
    void cases_AllNames_AreDistinct(@ForAll("caseNames") String name) {
        long matches = 0;
        for (BoardCase boardCase : BoardCase.values()) {
            if (boardCase.name().equals(name)) {
                matches++;
            }
        }

        assertEquals(
                1, matches, "Каждое имя кейса встречается ровно один раз: дубль имени сломал бы таблицу приоритетов");
    }

    @Provide
    Arbitrary<String> caseNames() {
        List<String> names = new ArrayList<>();
        for (BoardCase boardCase : BoardCase.values()) {
            names.add(boardCase.name());
        }
        return Arbitraries.of(names);
    }

    @Test
    void cases_Values_AreSortedByName() {
        List<BoardCase> values = List.of(BoardCase.values());
        List<BoardCase> sorted =
                values.stream().sorted(Comparator.comparing(BoardCase::name)).toList();

        assertEquals(
                sorted,
                values,
                "Обход values() упорядочен по имени константы: порядок влияет на детерминизм "
                        + "перебора кейсов источниками доски");
    }

    private static TicketTarget target() {
        return TicketTarget.at(new BlockCoord(10, 60, -5))
                .withSpecies(Species.SHEEP)
                .withScheme("pen")
                .withRelocate(new BlockCoord(10, 60, -5), new BlockCoord(30, 60, 10))
                .withSupply("bread", 1)
                .withCraft("minecraft:iron_pickaxe", 1)
                .withTransfer(
                        UUID.fromString("00000000-0000-0000-0000-0000000000a1"),
                        UUID.fromString("00000000-0000-0000-0000-0000000000a2"),
                        "bread",
                        1);
    }
}
