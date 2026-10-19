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
                "Предел в тесте, факт из production: полные ульи ищут в радиусе 16 от дома");
        assertEquals(
                5,
                HiveRules.smokeDepth(),
                "Предел в тесте, факт из production: дым костра достаёт улей на 5 блоков вниз");
        assertEquals(
                4,
                HiveRules.flowerRadius(),
                "Предел в тесте, факт из production: улей ставят, когда цветы не дальше 4 блоков");
        assertEquals(
                3, HiveRules.flowersMin(), "Предел в тесте, факт из production: рядом с ульем нужно минимум 3 цвета");
        assertEquals(6, HiveRules.hivePlanks(), "Предел в тесте, факт из production: улей стоит 6 досок");
        assertEquals(3, HiveRules.hiveHoneycomb(), "Предел в тесте, факт из production: улей стоит 3 соты");
        assertEquals(
                8,
                HiveRules.siteRadius(),
                "Предел в тесте, факт из production: место под улей ищут в радиусе 8 от дома");
    }

    @Test
    void fullHive_LevelFive_IsFull() {
        assertTrue(HiveRules.fullHive(5), "Улей уровня мёда 5 полон: только с него берут соты и мёд");
        assertFalse(HiveRules.fullHive(4), "Улей уровня 4 не полон: ранний сбор оставил бы мёд недозревшим");
        assertFalse(HiveRules.fullHive(0), "Пустой улей не цель сбора");
    }

    @Test
    void chooseTool_ShearsAndBottles_PrefersShears() {
        assertEquals(
                HiveRules.Tool.SHEARS,
                HiveRules.chooseTool(true, 5),
                "Ножницы идут первыми: они не тратятся и дают 3 соты за сбор");
        assertEquals(HiveRules.Tool.BOTTLE, HiveRules.chooseTool(false, 1), "Без ножниц стеклянная бутылка даёт мёд");
        assertEquals(
                HiveRules.Tool.NONE,
                HiveRules.chooseTool(false, 0),
                "Без инструмента сбор невозможен: мир обязан остаться нетронутым");
        assertThrows(
                IllegalArgumentException.class,
                () -> HiveRules.chooseTool(false, -1),
                "Отрицательные бутылки обязаны падать: минус прятал бы пустой запас");
    }

    @Test
    void campfireNeeded_NoSmoke_RequiresCampfire() {
        assertTrue(HiveRules.campfireNeeded(false), "Без дыма костёр обязателен: иначе пчёлы злятся");
        assertFalse(HiveRules.campfireNeeded(true), "Под ульем уже дымит костёр: второй не нужен");
    }

    @Test
    void smokeOffset_OneToFiveBelow_IsAccepted() {
        for (int offset = 1; offset <= 5; offset++) {
            assertTrue(
                    HiveRules.smokeOffsetWithinDepth(offset),
                    "Смещение " + offset + " входит в 1–5 блоков, которые проверяет дым");
        }
        assertFalse(HiveRules.smokeOffsetWithinDepth(0), "Смещение 0 — сам улей, костёр там не поставить");
        assertFalse(HiveRules.smokeOffsetWithinDepth(6), "Шестой блок вниз дым не достаёт: костёр там бесполезен");
        assertThrows(
                IllegalArgumentException.class,
                () -> HiveRules.smokeOffsetWithinDepth(-1),
                "Отрицательное смещение обязано падать: отсчёт идёт только вниз");
    }

    @Test
    void placementReady_CostAndFlowers_AllRequired() {
        assertTrue(HiveRules.placementReady(3, 6, 3), "Три соты, шесть досок и три цвета открывают постановку улья");
        assertFalse(HiveRules.placementReady(2, 6, 3), "Двух сотов на улей не хватает: крафт не состоится");
        assertFalse(HiveRules.placementReady(3, 5, 3), "Пяти досок на улей не хватает");
        assertFalse(HiveRules.placementReady(3, 6, 2), "Двух цветов мало: улей поставили бы без взятка");
    }

    @Property
    void chooseTool_AnyStock_NeverPicksMissingTool(
            @ForAll boolean shears, @ForAll @IntRange(min = 0, max = 32) int bottles) {
        HiveRules.Tool tool = HiveRules.chooseTool(shears, bottles);

        assertEquals(
                shears || bottles > 0,
                tool != HiveRules.Tool.NONE,
                "Инструмент выбирается ровно тогда, когда он есть: ножницы " + shears + ", бутылок " + bottles);
        if (tool == HiveRules.Tool.SHEARS) {
            assertTrue(shears, "Ножницы не выбирают без ножниц: инструмент " + tool);
        }
        if (tool == HiveRules.Tool.BOTTLE) {
            assertTrue(!shears && bottles > 0, "Бутылку выбирают только без ножниц и при запасе: бутылок " + bottles);
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
                        + flowers);
    }
}
