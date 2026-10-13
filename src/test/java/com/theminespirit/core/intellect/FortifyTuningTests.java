package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.base.CampTuning;
import com.theminespirit.core.script.CatalogLimits;
import org.junit.jupiter.api.Test;

class FortifyTuningTests {
    @Test
    void enemyMin_DeclaredValue_EqualsThree() {
        assertEquals(3, FortifyTuning.FORTIFY_ENEMY_MIN,
                "Укрепление поднимается при трёх врагах: больше — почти никогда, меньше — стройка на каждую мелочь");
    }

    @Test
    void blocksPerTick_DeclaredValue_EqualsTwo() {
        assertEquals(2, FortifyTuning.FORTIFY_BLOCKS_PER_TICK,
                "За тик строитель ставит не больше двух блоков: иначе стройка грузит серверный тик");
    }

    @Test
    void blockBudget_DeclaredValue_Equals64() {
        assertEquals(64, FortifyTuning.FORTIFY_BLOCK_BUDGET,
                "Бюджет укрепления 64 блока: меньше — укрытие 4×4 не достраивается, больше — дух тратит запас и время");
    }

    @Test
    void maxEnemies_DeclaredValue_EqualsSixteen() {
        assertEquals(16, FortifyTuning.FORTIFY_MAX_ENEMIES,
                "Обзор укрепления не шире тактического MAX_ENEMIES: иначе решение считает то, чего тактика не видит");
    }

    @Test
    void passageWidthMax_DeclaredValue_EqualsThree() {
        assertEquals(3, FortifyTuning.FORTIFY_PASSAGE_WIDTH_MAX,
                "Запечатывается проход шириной до 3: шире — шов съедает бюджет и не держит оборону");
    }

    @Test
    void dismantleDelay_DeclaredValue_Equals200() {
        assertEquals(200, FortifyTuning.FORTIFY_DISMANTLE_DELAY_TICKS,
                "Разбор своих блоков через 200 тиков покоя: раньше — дух разбирает под вернувшимися врагами");
    }

    @Test
    void shelterWallHeight_DeclaredValue_EqualsTwo() {
        assertEquals(2, FortifyTuning.SHELTER_WALL_HEIGHT,
                "Стены укрытия высотой 2: дух проходит, мобы не перепрыгивают, крыша не нужна");
    }

    @Test
    void campRadiusDefault_ProductionConstants_Match() {
        assertEquals(CampTuning.CAMP_RADIUS,
                CatalogLimits.CAMP_RADIUS_DEFAULT,
                "Радиус лагеря по умолчанию обязан совпадать с CampTuning: иначе скрипт ставит не ту зону, "
                        + "что ждёт оборона");
    }

    @Test
    void everyConstant_AllValues_PositiveAndFinite() {
        assertTrue(FortifyTuning.FORTIFY_ENEMY_MIN > 0, "Порог врагов положителен");
        assertTrue(FortifyTuning.FORTIFY_BLOCKS_PER_TICK > 0, "Порция постановки положительна");
        assertTrue(FortifyTuning.FORTIFY_BLOCK_BUDGET > 0, "Бюджет блоков положителен");
        assertTrue(FortifyTuning.FORTIFY_MAX_ENEMIES > 0, "Предел обзора положителен");
        assertTrue(FortifyTuning.FORTIFY_PASSAGE_WIDTH_MAX > 0, "Предел ширины прохода положителен");
        assertTrue(FortifyTuning.FORTIFY_DISMANTLE_DELAY_TICKS > 0, "Задержка разбора положительна");
        assertTrue(FortifyTuning.SHELTER_WALL_HEIGHT > 0, "Высота стен укрытия положительна");
        assertTrue(CatalogLimits.CAMP_RADIUS_MIN > 0
                && CatalogLimits.CAMP_RADIUS_DEFAULT >= CatalogLimits.CAMP_RADIUS_MIN
                && CatalogLimits.CAMP_RADIUS_DEFAULT <= CatalogLimits.CAMP_RADIUS_MAX,
                "Пределы радиуса лагеря положительны и упорядочены: иначе скрипт set_camp примет мусор");
    }
}
