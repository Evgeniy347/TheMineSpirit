package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SchematicActionsTests {
    @Test
    void buildEntry_ActionPlacedDirtPath_IsBuildEntryEvenWithoutItem() {
        assertTrue(
                SchematicActions.buildEntry("minecraft:dirt_path", false),
                "Дорожка входит в стройку действием лопаты, хотя предмета dirt_path нет: иначе BuildSchematic "
                        + "выкидывает её из схемы и сад остаётся без тропинок"
                        + " При смене buildEntry_ActionPlacedDirtPath_IsBuildEntryEvenWithoutItem проверь это утверждение.");
    }

    @Test
    void buildEntry_FarmlandWithoutItem_IsBuildEntryByHoe() {
        assertTrue(
                SchematicActions.buildEntry("minecraft:farmland", false),
                "Пашня входит в стройку действием тяпки: иначе BuildSchematic тратит блок земли на каждую грядку"
                        + " При смене buildEntry_FarmlandWithoutItem_IsBuildEntryByHoe проверь это утверждение.");
        assertTrue(
                SchematicActions.isTilled("minecraft:farmland"),
                "Пашню делает тяпка, а не BlockItem"
                        + " Иначе тест buildEntry_FarmlandWithoutItem_IsBuildEntryByHoe перестанет ловить эту поломку. При смене buildEntry_FarmlandWithoutItem_IsBuildEntryByHoe проверь это утверждение.");
        assertFalse(
                SchematicActions.isTilled("minecraft:dirt_path"),
                "Тропу топчет лопата, а не тяпка"
                        + " Иначе тест buildEntry_FarmlandWithoutItem_IsBuildEntryByHoe перестанет ловить эту поломку. При смене buildEntry_FarmlandWithoutItem_IsBuildEntryByHoe проверь это утверждение.");
    }

    @Test
    void itemOf_FarmlandAndWater_MapToCarriedItems() {
        assertTrue(
                SchematicBlocks.itemOf("farmland").endsWith("dirt"),
                "Пашню в смете считают землёй: другой предмет дух в руках не носит"
                        + " Иначе тест itemOf_FarmlandAndWater_MapToCarriedItems перестанет ловить эту поломку. При смене itemOf_FarmlandAndWater_MapToCarriedItems проверь это утверждение.");
        assertTrue(
                SchematicBlocks.itemOf("dirt_path").endsWith("dirt"),
                "Тропу в смете считают землёй: предмета dirt_path не существует, а смета иначе просит духа "
                        + "добыть его и стройка падает"
                        + " При смене itemOf_FarmlandAndWater_MapToCarriedItems проверь это утверждение.");
        assertTrue(
                SchematicBlocks.itemOf("water").endsWith("water_bucket"),
                "Воду в смете считают ведром: блок воды без ведра не поставить"
                        + " Иначе тест itemOf_FarmlandAndWater_MapToCarriedItems перестанет ловить эту поломку. При смене itemOf_FarmlandAndWater_MapToCarriedItems проверь это утверждение.");
        assertTrue(
                SchematicBlocks.itemOf("wheat").endsWith("wheat_seeds"),
                "Посев в смете считают семенами: колос в инвентаре не держат"
                        + " Иначе тест itemOf_FarmlandAndWater_MapToCarriedItems перестанет ловить эту поломку. При смене itemOf_FarmlandAndWater_MapToCarriedItems проверь это утверждение.");
    }

    @Test
    void toolKind_ActionBlocks_NameTheirTool() {
        assertEquals(
                "*_shovel",
                SchematicActions.toolKind("minecraft:dirt_path"),
                "Тропу топчут лопатой"
                        + " Иначе тест toolKind_ActionBlocks_NameTheirTool перестанет ловить эту поломку. При смене toolKind_ActionBlocks_NameTheirTool проверь это утверждение.");
        assertEquals(
                "*_hoe",
                SchematicActions.toolKind("minecraft:farmland"),
                "Пашню делает тяпка"
                        + " Иначе тест toolKind_ActionBlocks_NameTheirTool перестанет ловить эту поломку. При смене toolKind_ActionBlocks_NameTheirTool проверь это утверждение.");
        assertNull(
                SchematicActions.toolKind("minecraft:stone"),
                "Обычному блоку инструмент не нужен"
                        + " Иначе тест toolKind_ActionBlocks_NameTheirTool перестанет ловить эту поломку. При смене toolKind_ActionBlocks_NameTheirTool проверь это утверждение.");
    }

    @Test
    void buildEntry_StoneWithoutItem_IsNotBuildEntry() {
        assertFalse(
                SchematicActions.buildEntry("minecraft:stone", false),
                "Обычный блок без предмета в инвентаре не является действием: его нельзя ставить из воздуха"
                        + " Иначе тест buildEntry_StoneWithoutItem_IsNotBuildEntry перестанет ловить эту поломку. При смене buildEntry_StoneWithoutItem_IsNotBuildEntry проверь это утверждение.");
    }

    @Test
    void raiseItem_DirtPath_Dirt() {
        assertEquals(
                "minecraft:dirt",
                SchematicActions.raiseItem("minecraft:dirt_path"),
                "Подъём тропинки в воздушную клетку тратит блок земли: без этого дух приходит с пустым "
                        + "рюкзаком и падает с «нет земли»; При смене raiseItem в SchematicActions проверь "
                        + "BuildSupplyNeed и живой ремонт сада");
    }

    @Test
    void raiseItem_Farmland_Null() {
        assertNull(
                SchematicActions.raiseItem("minecraft:farmland"),
                "Пашня делается тяпкой и землю не тратит: иначе снабжение понесёт лишний блок под каждую "
                        + "грядку; При смене raiseItem проверь SchematicActionsTests и BuildSupplyNeed");
        assertNull(
                SchematicActions.raiseItem("minecraft:stone"),
                "Обычный блок ставится предметом, а не подъёмом: иначе raiseItem запросит землю вместо самого "
                        + "блока; При смене raiseItem проверь BuildSupplyNeed и подвоз стройки");
    }

    @Test
    void finishOrder_FieldBlocks_ReturnsWaterLightCropsOrder() {
        assertEquals(
                0,
                SchematicActions.finishOrder("minecraft:water"),
                "Вода ждёт готовой земли: иначе источник ставится в воздух и разливается ниже грядки. "
                        + "При смене finishOrder_FieldBlocks_ReturnsWaterLightCropsOrder проверь BuildPhase и живое поле.");
        assertEquals(
                1,
                SchematicActions.finishOrder("minecraft:torch"),
                "Освещение идёт до посевов: иначе семена могут выпасть ночью без факела. "
                        + "При смене finishOrder_FieldBlocks_ReturnsWaterLightCropsOrder проверь wheat_field_1.");
        assertEquals(
                2,
                SchematicActions.finishOrder("minecraft:wheat"),
                "Семена остаются последними: иначе до воды и факела поле ещё не готово. "
                        + "При смене finishOrder_FieldBlocks_ReturnsWaterLightCropsOrder проверь порядок finish-этапа.");
    }

    @Test
    void buildEntry_StoneWithItem_IsBuildEntry() {
        assertTrue(
                SchematicActions.buildEntry("minecraft:stone", true),
                "Обычный блок с предметом входит в стройку как материал"
                        + " Иначе тест buildEntry_StoneWithItem_IsBuildEntry перестанет ловить эту поломку. При смене buildEntry_StoneWithItem_IsBuildEntry проверь это утверждение.");
    }
}
