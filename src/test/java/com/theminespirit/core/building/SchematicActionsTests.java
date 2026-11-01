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
