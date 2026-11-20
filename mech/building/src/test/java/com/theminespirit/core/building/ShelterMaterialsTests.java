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
        assertEquals(
                0,
                ShelterMaterials.rank("cobblestone"),
                "Булыжник — первый по дешевизне"
                        + " Иначе тест rank_CobblestoneThenPlanks_LogsAndSmoothStoneCheapNo перестанет ловить эту поломку. При смене rank_CobblestoneThenPlanks_LogsAndSmoothStoneCheapNo проверь это утверждение.");
        assertEquals(
                0,
                ShelterMaterials.rank("minecraft:cobblestone"),
                "Пространство имён не меняет дешевизну"
                        + " Иначе тест rank_CobblestoneThenPlanks_LogsAndSmoothStoneCheapNo перестанет ловить эту поломку. При смене rank_CobblestoneThenPlanks_LogsAndSmoothStoneCheapNo проверь это утверждение.");
        assertTrue(
                ShelterMaterials.rank("oak_planks") >= 0,
                "Доски — дешёвый материал"
                        + " Иначе тест rank_CobblestoneThenPlanks_LogsAndSmoothStoneCheapNo перестанет ловить эту поломку. При смене rank_CobblestoneThenPlanks_LogsAndSmoothStoneCheapNo проверь это утверждение.");
        assertTrue(
                ShelterMaterials.rank("jungle_planks") >= 0,
                "Любая порода досок дешёвая"
                        + " Иначе тест rank_CobblestoneThenPlanks_LogsAndSmoothStoneCheapNo перестанет ловить эту поломку. При смене rank_CobblestoneThenPlanks_LogsAndSmoothStoneCheapNo проверь это утверждение.");
        assertFalse(
                ShelterMaterials.rank("jungle_log") >= 0,
                "Брёвна дороги: из бревна выходит четыре доски"
                        + " Иначе тест rank_CobblestoneThenPlanks_LogsAndSmoothStoneCheapNo перестанет ловить эту поломку. При смене rank_CobblestoneThenPlanks_LogsAndSmoothStoneCheapNo проверь это утверждение.");
        assertFalse(
                ShelterMaterials.rank("oak_log") >= 0,
                "Дубовые брёвна так же дороги"
                        + " Иначе тест rank_CobblestoneThenPlanks_LogsAndSmoothStoneCheapNo перестанет ловить эту поломку. При смене rank_CobblestoneThenPlanks_LogsAndSmoothStoneCheapNo проверь это утверждение.");
        assertFalse(
                ShelterMaterials.rank("smooth_stone") >= 0,
                "Гладкий камень дорог: его обжигают в печи"
                        + " Иначе тест rank_CobblestoneThenPlanks_LogsAndSmoothStoneCheapNo перестанет ловить эту поломку. При смене rank_CobblestoneThenPlanks_LogsAndSmoothStoneCheapNo проверь это утверждение.");
    }

    @Test
    void choose_BothEnough_CobblestoneWins() {
        ShelterMaterials.Choice choice = ShelterMaterials.choose(
                        Map.of("cobblestone", BODY, "cobblestone_slab", PORCH, "oak_planks", BODY * 2, "oak_slab", 4),
                        BODY,
                        PORCH)
                .orElseThrow();

        assertEquals(
                "cobblestone",
                choice.block(),
                "При полном запасе обоих материалов берётся булыжник"
                        + " Иначе тест choose_BothEnough_CobblestoneWins перестанет ловить эту поломку. При смене choose_BothEnough_CobblestoneWins проверь это утверждение.");
        assertEquals(
                "cobblestone_slab",
                choice.slab(),
                "Порог — полублок того же материала"
                        + " Иначе тест choose_BothEnough_CobblestoneWins перестанет ловить эту поломку. При смене choose_BothEnough_CobblestoneWins проверь это утверждение.");
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
                "Булыжника 44 на корпус не хватает — берутся доски, а не недоделка из булыжника"
                        + " Иначе тест choose_CobblestoneShort_PlanksUsed перестанет ловить эту поломку. При смене choose_CobblestoneShort_PlanksUsed проверь это утверждение.");
        assertEquals(
                "jungle_slab",
                choice.slab(),
                "Полублок порога обязан быть из того же материала"
                        + " Иначе тест choose_CobblestoneShort_PlanksUsed перестанет ловить эту поломку. При смене choose_CobblestoneShort_PlanksUsed проверь это утверждение.");
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
                "Досок хватает, но полублока порога нет — берётся булыжник с готовым полублоком"
                        + " Иначе тест choose_PlanksWithoutSlab_CobblestoneUsed перестанет ловить эту поломку. При смене choose_PlanksWithoutSlab_CobblestoneUsed проверь это утверждение.");
    }

    @Test
    void choose_NoCheapMaterial_Empty() {
        assertTrue(
                ShelterMaterials.choose(Map.of("jungle_log", 64, "smooth_stone", 64, "stone", 64), BODY, PORCH)
                        .isEmpty(),
                "Брёвна, гладкий и обычный камень без полублока не годятся: укрытие не начинают"
                        + " Иначе тест choose_NoCheapMaterial_Empty перестанет ловить эту поломку. При смене choose_NoCheapMaterial_Empty проверь это утверждение.");
    }

    @Test
    void choose_ShortStock_ReturnsCheapestForExactNumbers() {
        ShelterMaterials.Choice choice = ShelterMaterials.choose(
                        Map.of("cobblestone", 10, "cobblestone_slab", 1, "jungle_log", 64), BODY, PORCH)
                .orElseThrow();

        assertEquals(
                "cobblestone",
                choice.block(),
                "При нехватке выбор всё равно называет материал, чтобы причина сказала 10 из 130"
                        + " Иначе тест choose_ShortStock_ReturnsCheapestForExactNumbers перестанет ловить эту поломку. При смене choose_ShortStock_ReturnsCheapestForExactNumbers проверь это утверждение.");
    }

    @Test
    void slabFor_LogMaterial_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ShelterMaterials.slabFor("jungle_log"),
                "Полублок берётся только из булыжника или досок: из бревна полублока нет"
                        + " Иначе тест slabFor_LogMaterial_Rejected перестанет ловить эту поломку. При смене slabFor_LogMaterial_Rejected проверь это утверждение.");
    }
}
