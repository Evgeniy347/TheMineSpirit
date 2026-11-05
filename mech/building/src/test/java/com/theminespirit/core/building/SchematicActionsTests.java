package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SchematicActionsTests {
    @Test
    void buildEntry_ActionPlacedDirtPath_IsBuildEntryEvenWithoutItem() {
        assertTrue(
                SchematicActions.buildEntry("minecraft:dirt_path", false),
                "Дорожка входит в стройку действием лопаты, хотя предмета dirt_path нет: иначе BuildSchematic "
                        + "выкидывает её из схемы и сад остаётся без тропинок");
    }

    @Test
    void buildEntry_FarmlandWithoutItem_IsBuildEntryByHoe() {
        assertTrue(
                SchematicActions.buildEntry("minecraft:farmland", false),
                "Пашня входит в стройку действием тяпки: иначе BuildSchematic тратит блок земли на каждую грядку");
        assertTrue(SchematicActions.isTilled("minecraft:farmland"), "Пашню делает тяпка, а не BlockItem");
        assertFalse(SchematicActions.isTilled("minecraft:dirt_path"), "Тропу топчет лопата, а не тяпка");
    }

    @Test
    void itemOf_FarmlandAndWater_MapToCarriedItems() {
        assertTrue(
                SchematicBlocks.itemOf("farmland").endsWith("dirt"),
                "Пашню в смете считают землёй: другой предмет дух в руках не носит");
        assertTrue(
                SchematicBlocks.itemOf("water").endsWith("water_bucket"),
                "Воду в смете считают ведром: блок воды без ведра не поставить");
        assertTrue(
                SchematicBlocks.itemOf("wheat").endsWith("wheat_seeds"),
                "Посев в смете считают семенами: колос в инвентаре не держат");
    }

    @Test
    void buildEntry_StoneWithoutItem_IsNotBuildEntry() {
        assertFalse(
                SchematicActions.buildEntry("minecraft:stone", false),
                "Обычный блок без предмета в инвентаре не является действием: его нельзя ставить из воздуха");
    }

    @Test
    void buildEntry_StoneWithItem_IsBuildEntry() {
        assertTrue(
                SchematicActions.buildEntry("minecraft:stone", true),
                "Обычный блок с предметом входит в стройку как материал");
    }
}
