package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BoardMaterialsTests {
    private static BoardTile tile(
            String boardCase, String status, Map<String, Integer> missing, String supplyItem, int supplyCount) {
        return new BoardTile(
                "board-1",
                boardCase,
                1,
                10,
                null,
                status,
                BoardTileGroup.AVAILABLE,
                missing,
                List.of(),
                0,
                0,
                0,
                0,
                supplyItem,
                supplyCount);
    }

    @Test
    void of_GatherTicket_ShowsItemAndRemainingCount() {
        List<BoardMaterials.Material> materials =
                BoardMaterials.of(tile("GATHER_MATERIALS", "PENDING", Map.of(), "minecraft:oak_log", 7));

        assertEquals(
                List.of(new BoardMaterials.Material(BoardMaterials.Kind.GATHER, "minecraft:oak_log", 7)),
                materials,
                "Задание добычи обязано показывать предмет и количество, которого не хватает до завершения");
    }

    @Test
    void of_SupplyTicket_ShowsItemAndCount() {
        List<BoardMaterials.Material> materials =
                BoardMaterials.of(tile("SUPPLY_SITE", "PENDING", Map.of(), "minecraft:dirt", 12));

        assertEquals(
                List.of(new BoardMaterials.Material(BoardMaterials.Kind.SUPPLY, "minecraft:dirt", 12)),
                materials,
                "Снабжение supply:* — это тоже материалы: экран обязан показать предмет и число");
    }

    @Test
    void of_MultipleMissingItems_ShowsEachIconInStableOrder() {
        List<BoardMaterials.Material> materials =
                BoardMaterials.of(tile("BUILD_HOUSE", "WAITING_RESOURCES", Map.of("oak_log", 3, "dirt", 5), null, 0));

        assertEquals(
                List.of(
                        new BoardMaterials.Material(BoardMaterials.Kind.MISSING, "dirt", 5),
                        new BoardMaterials.Material(BoardMaterials.Kind.MISSING, "oak_log", 3)),
                materials,
                "Несколько недостающих предметов показывают несколькими иконками в устойчивом порядке");
    }

    @Test
    void of_WaitingResources_ShowsHowManyAreStillMissing() {
        List<BoardMaterials.Material> materials =
                BoardMaterials.of(tile("BUILD_HOUSE", "WAITING_RESOURCES", Map.of("stone", 4), null, 0));

        assertEquals(4, materials.get(0).count(), "Ожидание материалов показывает нехватку, а не полную нужду");
        assertEquals(BoardMaterials.Kind.MISSING, materials.get(0).kind(), "Нехватка — отдельный вид материала");
    }

    @Test
    void of_TaskWithoutMaterials_ShowsNothing() {
        assertTrue(
                BoardMaterials.of(tile("BUILD_HOUSE", "PENDING", Map.of(), null, 0))
                        .isEmpty(),
                "Задание без материалов не рисует ни иконок, ни подписи");
    }

    @Test
    void of_SupplyAndMissingSameItem_ShowsSupplyOnce() {
        List<BoardMaterials.Material> materials =
                BoardMaterials.of(tile("GATHER_MATERIALS", "WAITING_RESOURCES", Map.of("oak_log", 2), "oak_log", 7));

        assertEquals(1, materials.size(), "Один предмет — одна иконка, даже если он есть и в нехватке, и в запасе");
        assertEquals(7, materials.get(0).count(), "Показывают цель задания, а не промежуточную нехватку");
    }

    @Test
    void of_OtherCaseWithSupply_DoesNotShowSupplyIcon() {
        assertTrue(
                BoardMaterials.of(tile("SMELT_CHARCOAL", "PENDING", Map.of(), "oak_log", 4))
                        .isEmpty(),
                "Не всякий supplyItem — то, что дух добывает: иконку рисуют только для добычи и снабжения");
    }
}
