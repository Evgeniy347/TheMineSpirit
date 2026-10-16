package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

class BoardCaseTests {

    @Test
    void caseSet_IsCompleteAndFixed() {
        Set<String> actual = Arrays.stream(BoardCase.values()).map(Enum::name).collect(Collectors.toSet());

        Set<String> expected = Set.of(
                "CLEAR_GROUNDS", "STOCK_CHESTS", "CRAFTING_STATION", "CRAFT_TOOLS", "BUILD_MINE",
                "BUILD_FENCE", "LIGHTING", "BUILD_HOUSE", "REPAIR_FENCE", "REPAIR_BUILDING",
                "FILL_CRATER", "CAPTURE_ANIMAL", "BUILD_PEN", "LURE_ANIMAL", "DISMANTLE_SHELTER",
                "SUPPLY_SITE", "ENCHANT_ROOM", "ENCHANT_SHELVES");

        assertEquals(expected, actual,
                "Набор кейсов доски обязан совпадать с чеклистом 35: каждый вид задания получает "
                        + "приоритет, гейт и план по своему имени; лишний кейс — сирота в "
                        + "таблице BoardRules, потерянный — задание без источника");
    }

    @Test
    void caseNames_AreStableAndReadable() {
        for (BoardCase boardCase : BoardCase.values()) {
            assertEquals(boardCase, BoardCase.valueOf(boardCase.name()),
                    "Имя кейса должно читаться обратно через valueOf: по имени кейс ищется "
                            + "в таблице приоритетов и в наблюдателе, переименование ломает данные");
        }
    }

    @Property
    void caseNames_AreAllDistinct() {
        Set<String> names = Arrays.stream(BoardCase.values()).map(Enum::name).collect(Collectors.toSet());

        assertEquals(BoardCase.values().length, names.size(),
                "Имена кейсов обязаны быть различны: два вида задания с одним именем слились "
                        + "бы в одну строку таблицы приоритетов, и одно задание не получило бы гейт");
    }

    @Property
    void iteration_IsOrderedByConstantName() {
        String[] names = Arrays.stream(BoardCase.values()).map(Enum::name).toArray(String[]::new);
        String[] sorted = names.clone();
        Arrays.sort(sorted);

        assertEquals(Arrays.asList(sorted), Arrays.asList(names),
                "Обход кейсов обязан быть детерминирован: порядок по имени константы держит "
                        + "набор и свойства воспроизводимыми на любой JVM");
    }
}
