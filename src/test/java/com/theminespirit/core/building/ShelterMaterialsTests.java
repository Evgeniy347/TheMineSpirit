package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ShelterMaterialsTests {
    private static final int BODY = 130;
    private static final int PORCH = 1;

    @Test
    void rank_CobblestoneThenPlanks_LogsAndSmoothStoneCheapNo() {
        assertEquals(0, ShelterMaterials.rank("cobblestone"), "Булыжник — первый по дешевизне");
        assertEquals(0, ShelterMaterials.rank("minecraft:cobblestone"), "Пространство имён не меняет дешевизну");
        assertTrue(ShelterMaterials.rank("oak_planks") >= 0, "Доски — дешёвый материал");
        assertTrue(ShelterMaterials.rank("jungle_planks") >= 0, "Любая порода досок дешёвая");
        assertFalse(ShelterMaterials.rank("jungle_log") >= 0, "Брёвна дороги: из бревна выходит четыре доски");
        assertFalse(ShelterMaterials.rank("oak_log") >= 0, "Дубовые брёвна так же дороги");
        assertFalse(ShelterMaterials.rank("smooth_stone") >= 0, "Гладкий камень дорог: его обжигают в печи");
    }

    @Test
    void choose_BothEnough_CobblestoneWins() {
        ShelterMaterials.Choice choice = ShelterMaterials.choose(
                        Map.of("cobblestone", BODY, "cobblestone_slab", PORCH, "oak_planks", BODY * 2, "oak_slab", 4),
                        BODY,
                        PORCH)
                .orElseThrow();

        assertEquals("cobblestone", choice.block(), "При полном запасе обоих материалов берётся булыжник");
        assertEquals("cobblestone_slab", choice.slab(), "Порог — полублок того же материала");
    }

    @Test
    void choose_CobblestoneShort_PlanksUsed() {
        ShelterMaterials.Choice choice = ShelterMaterials.choose(
                        Map.of("cobblestone", 44, "cobblestone_slab", 1, "jungle_planks", BODY, "jungle_slab", 1),
                        BODY,
                        PORCH)
                .orElseThrow();

        assertEquals(
                "jungle_planks",
                choice.block(),
                "Булыжника 44 на корпус не хватает — берутся доски, а не недоделка из булыжника");
        assertEquals("jungle_slab", choice.slab(), "Полублок порога обязан быть из того же материала");
    }

    @Test
    void choose_PlanksWithoutSlab_CobblestoneUsed() {
        ShelterMaterials.Choice choice = ShelterMaterials.choose(
                        Map.of("cobblestone", BODY, "cobblestone_slab", 1, "oak_planks", BODY, "oak_slab", 0),
                        BODY,
                        PORCH)
                .orElseThrow();

        assertEquals(
                "cobblestone",
                choice.block(),
                "Досок хватает, но полублока порога нет — берётся булыжник с готовым полублоком");
    }

    @Test
    void choose_NoCheapMaterial_Empty() {
        assertTrue(
                ShelterMaterials.choose(Map.of("jungle_log", 64, "smooth_stone", 64, "stone", 64), BODY, PORCH)
                        .isEmpty(),
                "Брёвна, гладкий и обычный камень без полублока не годятся: укрытие не начинают");
    }

    @Test
    void choose_ShortStock_ReturnsCheapestForExactNumbers() {
        ShelterMaterials.Choice choice = ShelterMaterials.choose(
                        Map.of("cobblestone", 10, "cobblestone_slab", 1, "jungle_log", 64), BODY, PORCH)
                .orElseThrow();

        assertEquals(
                "cobblestone",
                choice.block(),
                "При нехватке выбор всё равно называет материал, чтобы причина сказала 10 из 130");
    }

    @Test
    void slabFor_LogMaterial_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ShelterMaterials.slabFor("jungle_log"),
                "Полублок берётся только из булыжника или досок: из бревна полублока нет");
    }
}
