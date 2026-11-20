package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.ui.api.BoardTileGroup;
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

    @Test
    void of_FillCraterWithStock_ShowsDirtHaveAndNeed() {
        BoardTile crater = fillCraterTile(Map.of(), Map.of("dirt", 416), Map.of("dirt", 4688));

        List<BoardMaterials.Material> materials = BoardMaterials.of(crater);

        assertEquals(
                List.of(new BoardMaterials.Material(BoardMaterials.Kind.NEED, "dirt", 416, 4688)),
                materials,
                "Карточка «Засыпать воронку» обязана показать землю и «есть / надо»: без этого игрок "
                        + "не знает, сколько земли нужно на яму; При смене FILL_CRATER-тикетов проверь fillNeed");
        assertTrue(
                !BoardMaterials.shortage(materials.get(0)),
                "Земли 4688 при нужде 416: звезды нехватки быть не должно, иначе игрок побежит добывать "
                        + "землю, которой и так хватает; При смене расчёта stock в BoardMaterials проверь "
                        + "shortage и карточку ремонта");
    }

    @Test
    void of_FillCraterShortStock_StarWithoutDuplicateMissing() {
        BoardTile crater = fillCraterTile(Map.of("dirt", 316), Map.of("dirt", 416), Map.of("dirt", 100));

        List<BoardMaterials.Material> materials = BoardMaterials.of(crater);

        assertEquals(
                List.of(new BoardMaterials.Material(BoardMaterials.Kind.NEED, "dirt", 416, 100)),
                materials,
                "При нехватке земли карточка показывает нужду и звезду, а не вторую строку «не хватает»: "
                        + "иначе число, которое надо добрать, теряется среди дублей и гейт склада выглядит "
                        + "сломанным; При смене BoardMaterials.needRows проверь REPAIR_BUILDING");
        assertTrue(
                BoardMaterials.shortage(materials.get(0)),
                "Земли 100 при нужде 416: звезда нехватки обязана звать добычу, иначе засыпка молча ждёт "
                        + "и воронка стоит до конца этапа; При смене BoardMaterials.shortage проверь карточку "
                        + "FILL_CRATER");
    }

    private static BoardTile fillCraterTile(
            Map<String, Integer> missing, Map<String, Integer> requiredItems, Map<String, Integer> stock) {
        return new BoardTile(
                "world:backfill:-206,-526:16x26",
                "FILL_CRATER",
                1,
                55,
                null,
                "PENDING",
                BoardTileGroup.AVAILABLE,
                missing,
                List.of(),
                0,
                0,
                0,
                0,
                null,
                0,
                List.of("target=-206,66,-526"),
                requiredItems,
                stock);
    }

    @Test
    void of_RepairWithStock_ShowsHaveAndNeedWithoutDuplicateMissing() {
        BoardTile repair = new BoardTile(
                "repair:hut",
                "REPAIR_BUILDING",
                1,
                90,
                null,
                "PENDING",
                BoardTileGroup.AVAILABLE,
                Map.of("minecraft:cobblestone", 4),
                List.of(),
                0,
                0,
                0,
                0,
                null,
                0,
                List.of("building=Хижина"),
                Map.of("minecraft:dirt", 5, "minecraft:cobblestone", 8),
                Map.of("minecraft:dirt", 10240, "minecraft:cobblestone", 4));

        List<BoardMaterials.Material> materials = BoardMaterials.of(repair);

        assertEquals(
                List.of(
                        new BoardMaterials.Material(BoardMaterials.Kind.NEED, "minecraft:cobblestone", 8, 4),
                        new BoardMaterials.Material(BoardMaterials.Kind.NEED, "minecraft:dirt", 5, 10240)),
                materials,
                "Карточка показывает «есть / надо» и не дублирует нехватку отдельной строкой");
        assertTrue(
                BoardMaterials.shortage(materials.get(0)),
                "Звезда нехватки — только у предмета, которого на складе меньше: булыжника 4 из 8");
        assertTrue(
                !BoardMaterials.shortage(materials.get(1)),
                "Земли 10 240 при нужде 528: звезды быть не должно, склад закрывает нужду");
    }

    @Test
    void of_RepairBuilding_ShowsNeedThenMissing() {
        BoardTile repair = new BoardTile(
                "repair:hut",
                "REPAIR_BUILDING",
                1,
                90,
                null,
                "PENDING",
                BoardTileGroup.AVAILABLE,
                Map.of("minecraft:cobblestone", 3),
                List.of(),
                0,
                0,
                0,
                0,
                null,
                0,
                List.of("building=Хижина"),
                Map.of("minecraft:dirt", 5, "minecraft:cobblestone", 8));

        assertEquals(
                List.of(
                        new BoardMaterials.Material(BoardMaterials.Kind.NEED, "minecraft:cobblestone", 8),
                        new BoardMaterials.Material(BoardMaterials.Kind.NEED, "minecraft:dirt", 5),
                        new BoardMaterials.Material(BoardMaterials.Kind.MISSING, "minecraft:cobblestone", 3)),
                BoardMaterials.of(repair),
                "Карточка ремонта обязана показать весь список «Нужно», а следом — только нехватку склада");
    }
}
