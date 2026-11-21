package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class HiveRulesTests {

    @Test
    void defaults_FromClasspath_MatchChecklist() {
        assertEquals(
                16,
                HiveRules.hiveRadius(),
                "Предел в тесте, факт из production: полные ульи ищут в радиусе 16 от дома"
                        + " Иначе тест defaults_FromClasspath_MatchChecklist перестанет ловить эту поломку. При смене defaults_FromClasspath_MatchChecklist проверь это утверждение.");
        assertEquals(
                5,
                HiveRules.smokeDepth(),
                "Предел в тесте, факт из production: дым костра достаёт улей на 5 блоков вниз"
                        + " Иначе тест defaults_FromClasspath_MatchChecklist перестанет ловить эту поломку. При смене defaults_FromClasspath_MatchChecklist проверь это утверждение.");
        assertEquals(
                4,
                HiveRules.flowerRadius(),
                "Предел в тесте, факт из production: улей ставят, когда цветы не дальше 4 блоков"
                        + " Иначе тест defaults_FromClasspath_MatchChecklist перестанет ловить эту поломку. При смене defaults_FromClasspath_MatchChecklist проверь это утверждение.");
        assertEquals(
                3,
                HiveRules.flowersMin(),
                "Предел в тесте, факт из production: рядом с ульем нужно минимум 3 цвета"
                        + " Иначе тест defaults_FromClasspath_MatchChecklist перестанет ловить эту поломку. При смене defaults_FromClasspath_MatchChecklist проверь это утверждение.");
        assertEquals(
                6,
                HiveRules.hivePlanks(),
                "Предел в тесте, факт из production: улей стоит 6 досок"
                        + " Иначе тест defaults_FromClasspath_MatchChecklist перестанет ловить эту поломку. При смене defaults_FromClasspath_MatchChecklist проверь это утверждение.");
        assertEquals(
                3,
                HiveRules.hiveHoneycomb(),
                "Предел в тесте, факт из production: улей стоит 3 соты"
                        + " Иначе тест defaults_FromClasspath_MatchChecklist перестанет ловить эту поломку. При смене defaults_FromClasspath_MatchChecklist проверь это утверждение.");
        assertEquals(
                8,
                HiveRules.siteRadius(),
                "Предел в тесте, факт из production: место под улей ищут в радиусе 8 от дома"
                        + " Иначе тест defaults_FromClasspath_MatchChecklist перестанет ловить эту поломку. При смене defaults_FromClasspath_MatchChecklist проверь это утверждение.");
    }

    @Test
    void fullHive_LevelFive_IsFull() {
        assertTrue(
                HiveRules.fullHive(5),
                "Улей уровня мёда 5 полон: только с него берут соты и мёд"
                        + " Иначе тест fullHive_LevelFive_IsFull перестанет ловить эту поломку. При смене fullHive_LevelFive_IsFull проверь это утверждение.");
        assertFalse(
                HiveRules.fullHive(4),
                "Улей уровня 4 не полон: ранний сбор оставил бы мёд недозревшим"
                        + " Иначе тест fullHive_LevelFive_IsFull перестанет ловить эту поломку. При смене fullHive_LevelFive_IsFull проверь это утверждение.");
        assertFalse(
                HiveRules.fullHive(0),
                "Пустой улей не цель сбора"
                        + " Иначе тест fullHive_LevelFive_IsFull перестанет ловить эту поломку. При смене fullHive_LevelFive_IsFull проверь это утверждение.");
    }

    @Test
    void chooseTool_ShearsAndBottles_PrefersShears() {
        assertEquals(
                HiveRules.Tool.SHEARS,
                HiveRules.chooseTool(true, 5),
                "Ножницы идут первыми: они не тратятся и дают 3 соты за сбор"
                        + " Иначе тест chooseTool_ShearsAndBottles_PrefersShears перестанет ловить эту поломку. При смене chooseTool_ShearsAndBottles_PrefersShears проверь это утверждение.");
        assertEquals(
                HiveRules.Tool.BOTTLE,
                HiveRules.chooseTool(false, 1),
                "Без ножниц стеклянная бутылка даёт мёд"
                        + " Иначе тест chooseTool_ShearsAndBottles_PrefersShears перестанет ловить эту поломку. При смене chooseTool_ShearsAndBottles_PrefersShears проверь это утверждение.");
        assertEquals(
                HiveRules.Tool.NONE,
                HiveRules.chooseTool(false, 0),
                "Без инструмента сбор невозможен: мир обязан остаться нетронутым"
                        + " Иначе тест chooseTool_ShearsAndBottles_PrefersShears перестанет ловить эту поломку. При смене chooseTool_ShearsAndBottles_PrefersShears проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> HiveRules.chooseTool(false, -1),
                "Отрицательные бутылки обязаны падать: минус прятал бы пустой запас"
                        + " Иначе тест chooseTool_ShearsAndBottles_PrefersShears перестанет ловить эту поломку. При смене chooseTool_ShearsAndBottles_PrefersShears проверь это утверждение.");
    }

    @Test
    void campfireNeeded_NoSmoke_RequiresCampfire() {
        assertTrue(
                HiveRules.campfireNeeded(false),
                "Без дыма костёр обязателен: иначе пчёлы злятся"
                        + " При смене campfireNeeded_NoSmoke_RequiresCampfire проверь это утверждение.");
        assertFalse(
                HiveRules.campfireNeeded(true),
                "Под ульем уже дымит костёр: второй не нужен"
                        + " Иначе тест campfireNeeded_NoSmoke_RequiresCampfire перестанет ловить эту поломку. При смене campfireNeeded_NoSmoke_RequiresCampfire проверь это утверждение.");
    }

    @Test
    void smokeOffset_OneToFiveBelow_IsAccepted() {
        for (int offset = 1; offset <= 5; offset++) {
            assertTrue(
                    HiveRules.smokeOffsetWithinDepth(offset),
                    "Смещение " + offset + " входит в 1–5 блоков, которые проверяет дым"
                            + " Иначе тест smokeOffset_OneToFiveBelow_IsAccepted перестанет ловить эту поломку. При смене smokeOffset_OneToFiveBelow_IsAccepted проверь это утверждение.");
        }
        assertFalse(
                HiveRules.smokeOffsetWithinDepth(0),
                "Смещение 0 — сам улей, костёр там не поставить"
                        + " Иначе тест smokeOffset_OneToFiveBelow_IsAccepted перестанет ловить эту поломку. При смене smokeOffset_OneToFiveBelow_IsAccepted проверь это утверждение.");
        assertFalse(
                HiveRules.smokeOffsetWithinDepth(6),
                "Шестой блок вниз дым не достаёт: костёр там бесполезен"
                        + " Иначе тест smokeOffset_OneToFiveBelow_IsAccepted перестанет ловить эту поломку. При смене smokeOffset_OneToFiveBelow_IsAccepted проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> HiveRules.smokeOffsetWithinDepth(-1),
                "Отрицательное смещение обязано падать: отсчёт идёт только вниз"
                        + " Иначе тест smokeOffset_OneToFiveBelow_IsAccepted перестанет ловить эту поломку. При смене smokeOffset_OneToFiveBelow_IsAccepted проверь это утверждение.");
    }

    @Test
    void placementReady_CostAndFlowers_AllRequired() {
        assertTrue(
                HiveRules.placementReady(3, 6, 3),
                "Три соты, шесть досок и три цвета открывают постановку улья"
                        + " Иначе тест placementReady_CostAndFlowers_AllRequired перестанет ловить эту поломку. При смене placementReady_CostAndFlowers_AllRequired проверь это утверждение.");
        assertFalse(
                HiveRules.placementReady(2, 6, 3),
                "Двух сотов на улей не хватает: крафт не состоится"
                        + " Иначе тест placementReady_CostAndFlowers_AllRequired перестанет ловить эту поломку. При смене placementReady_CostAndFlowers_AllRequired проверь это утверждение.");
        assertFalse(
                HiveRules.placementReady(3, 5, 3),
                "Пяти досок на улей не хватает"
                        + " Иначе тест placementReady_CostAndFlowers_AllRequired перестанет ловить эту поломку. При смене placementReady_CostAndFlowers_AllRequired проверь это утверждение.");
        assertFalse(
                HiveRules.placementReady(3, 6, 2),
                "Двух цветов мало: улей поставили бы без взятка"
                        + " Иначе тест placementReady_CostAndFlowers_AllRequired перестанет ловить эту поломку. При смене placementReady_CostAndFlowers_AllRequired проверь это утверждение.");
    }

    @Property
    void chooseTool_AnyStock_NeverPicksMissingTool(
            @ForAll boolean shears, @ForAll @IntRange(min = 0, max = 32) int bottles) {
        HiveRules.Tool tool = HiveRules.chooseTool(shears, bottles);

        assertEquals(
                shears || bottles > 0,
                tool != HiveRules.Tool.NONE,
                "Инструмент выбирается ровно тогда, когда он есть: ножницы " + shears + ", бутылок " + bottles
                        + " Иначе тест chooseTool_AnyStock_NeverPicksMissingTool перестанет ловить эту поломку. При смене chooseTool_AnyStock_NeverPicksMissingTool проверь это утверждение.");
        if (tool == HiveRules.Tool.SHEARS) {
            assertTrue(
                    shears,
                    "Ножницы не выбирают без ножниц: инструмент " + tool
                            + " Иначе тест chooseTool_AnyStock_NeverPicksMissingTool перестанет ловить эту поломку. При смене chooseTool_AnyStock_NeverPicksMissingTool проверь это утверждение.");
        }
        if (tool == HiveRules.Tool.BOTTLE) {
            assertTrue(
                    !shears && bottles > 0,
                    "Бутылку выбирают только без ножниц и при запасе: бутылок " + bottles
                            + " Иначе тест chooseTool_AnyStock_NeverPicksMissingTool перестанет ловить эту поломку. При смене chooseTool_AnyStock_NeverPicksMissingTool проверь это утверждение.");
        }
    }

    @Property
    void placementReady_AnyStock_MatchesAllGates(
            @ForAll @IntRange(min = 0, max = 12) int honeycomb,
            @ForAll @IntRange(min = 0, max = 12) int planks,
            @ForAll @IntRange(min = 0, max = 8) int flowers) {
        boolean expected = honeycomb >= HiveRules.hiveHoneycomb()
                && planks >= HiveRules.hivePlanks()
                && flowers >= HiveRules.flowersMin();

        assertEquals(
                expected,
                HiveRules.placementReady(honeycomb, planks, flowers),
                "Улей ставится только при всех трёх условиях: сотов " + honeycomb + ", досок " + planks + ", цветов "
                        + flowers
                        + " Иначе тест placementReady_AnyStock_MatchesAllGates перестанет ловить эту поломку. При смене placementReady_AnyStock_MatchesAllGates проверь это утверждение.");
    }
}
