package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

class BoardCaseTests {

    @Test
    void caseSet_Is_Complete() {
        Set<String> actual = Arrays.stream(BoardCase.values()).map(Enum::name).collect(Collectors.toSet());

        Set<String> expected = Set.of(
                "CLEAR_GROUNDS", "COLLECT_DROPS", "STOCK_CHESTS", "CRAFTING_STATION", "CRAFT_BED",
                "CRAFT_SHEARS", "BUILD_MINE", "CRAFT_WOODEN_PICKAXE", "CRAFT_WOODEN_AXE",
                "CRAFT_WOODEN_SHOVEL", "CRAFT_STONE_PICKAXE", "CRAFT_STONE_AXE", "CRAFT_STONE_SHOVEL",
                "CRAFT_STONE_SWORD", "CRAFT_ANVIL", "CRAFT_SMITHING_TABLE", "CRAFT_SMOKER",
                "BUILD_FENCE", "LIGHTING", "BUILD_HOUSE", "REPAIR_FENCE",
                "REPAIR_BUILDING", "REPAIR_TOOL", "FILL_CRATER", "FISH", "CAPTURE_ANIMAL", "BUILD_PEN",
                "LURE_ANIMAL",
                "DISMANTLE_SHELTER", "SUPPLY_SITE", "GATHER_MATERIALS", "ENCHANT_ROOM",
                "ENCHANT_SHELVES", "BREED_ANIMALS", "EAT_TO_FULL", "HERD_CULL", "HUNT_WILD_SHEEP",
                "RELOCATE_BUILDING", "RESTOCK_FOOD", "SHEAR_SHEEP", "TRANSFER_ITEM", "WEAVE_WOOL");

        assertEquals(expected, actual,
                "Набор кейсов доски обязан совпадать с чеклистами 36, 37Б и 39 (37: 32 из v2/37Б "
                        + "плюс CRAFT_SHEARS, SHEAR_SHEEP, HUNT_WILD_SHEEP, WEAVE_WOOL, CRAFT_BED): "
                        + "каждый вид задания получает приоритет, гейт и план по своему имени; "
                        + "лишний кейс — сирота в таблице BoardRules, потерянный — задание без источника");
    }

    @Test
    void caseNames_Are_Stable() {
        for (BoardCase boardCase : BoardCase.values()) {
            assertEquals(boardCase, BoardCase.valueOf(boardCase.name()),
                    "Имя кейса должно читаться обратно через valueOf: по имени кейс ищется "
                            + "в таблице приоритетов и в наблюдателе, переименование ломает данные");
        }
    }

    @Property
    void caseNames_Are_AllDistinct() {
        Set<String> names = Arrays.stream(BoardCase.values()).map(Enum::name).collect(Collectors.toSet());

        assertEquals(BoardCase.values().length, names.size(),
                "Имена кейсов обязаны быть различны: два вида задания с одним именем слились "
                        + "бы в одну строку таблицы приоритетов, и одно задание не получило бы гейт");
    }

    @Property
    void iteration_Is_OrderedByName() {
        String[] names = Arrays.stream(BoardCase.values()).map(Enum::name).toArray(String[]::new);
        String[] sorted = names.clone();
        Arrays.sort(sorted);

        assertEquals(Arrays.asList(sorted), Arrays.asList(names),
                "Обход кейсов обязан быть детерминирован: порядок по имени константы держит "
                        + "набор и свойства воспроизводимыми на любой JVM");
    }
}
