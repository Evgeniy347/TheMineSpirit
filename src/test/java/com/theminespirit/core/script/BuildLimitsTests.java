package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BuildLimitsTests {
    @Test
    void digSizeMin_LimitValue_EqualsOne() {
        assertEquals(1, CatalogLimits.DIG_SIZE_MIN,
                "Сторона куба dig_area начинается с 1: меньший куб нечего копать, число меняет геймдизайн только с согласия");
    }

    @Test
    void digSizeMax_LimitValue_EqualsSixteen() {
        assertEquals(16, CatalogLimits.DIG_SIZE_MAX,
                "Сторона куба dig_area ограничена 16: больший куб роет слишком долго и грузит тик, число меняет геймдизайн только с согласия");
    }

    @Test
    void buildVolumeMax_LimitValue_Equals4096() {
        assertEquals(4096, CatalogLimits.BUILD_VOLUME_MAX,
                "Объём стройки ограничен 4096 (16³): больший объём держит духа часами, число меняет геймдизайн только с согласия");
    }

    @Test
    void schematicSizeMax_LimitValue_Equals32() {
        assertEquals(32, CatalogLimits.SCHEMATIC_SIZE_MAX,
                "Сторона схемы ограничена 32: большая схема не влезает в память площадки, число меняет геймдизайн только с согласия");
    }

    @Test
    void schematicBlocksMax_LimitValue_Equals4096() {
        assertEquals(4096, CatalogLimits.SCHEMATIC_BLOCKS_MAX,
                "Число блоков схемы ограничено 4096: больше блоков строятся слишком долго, число меняет геймдизайн только с согласия");
    }

    @Test
    void blocksPerTick_LimitValue_EqualsOne() {
        assertEquals(1, CatalogLimits.BLOCKS_PER_TICK,
                "За тик меняется ровно один блок: два блока за тик удваивают нагрузку серверного тика, число меняет геймдизайн только с согласия");
    }

    @Test
    void farmPlotSide_LimitValues_MatchDeclared() {
        assertEquals(1, CatalogLimits.FARM_PLOT_SIDE_MIN,
                "Сторона грядки начинается с 1: грядка из одной клетки — законная работа");
        assertEquals(16, CatalogLimits.FARM_PLOT_SIDE_MAX,
                "Сторона грядки ограничена 16, как радиус фермы: больше держит духа часами");
    }

    @Test
    void shelterSide_LimitValues_MatchDeclared() {
        assertEquals(3, CatalogLimits.SHELTER_SIDE_MIN,
                "Укрытие начинается с 3×3: в меньшем дух не встаёт внутри");
        assertEquals(4, CatalogLimits.SHELTER_SIDE_MAX,
                "Укрытие кончается на 4×4: это размер быстрого укрепления Fortify");
        assertEquals(64, CatalogLimits.SHELTER_BLOCKS_MAX,
                "Бюджет укрытия 64 блока: стены 4×4 с крышей помещаются, лишнего дух не ставит");
    }

    @Test
    void fence_LimitValues_MatchDeclared() {
        assertEquals(3, CatalogLimits.FENCE_CORNERS_MIN,
                "Контур забора начинается с трёх углов: двумя точками задаётся линия, а не забор");
        assertEquals(64, CatalogLimits.FENCE_CORNERS_MAX,
                "Углов контура не больше 64: длинный контур не влезает в один проход");
        assertEquals(512, CatalogLimits.FENCE_BLOCKS_MAX,
                "Клеток забора не больше 512: больше блоков дух ставит слишком долго");
    }

    @Test
    void campRadius_LimitValues_MatchDeclared() {
        assertEquals(1, CatalogLimits.CAMP_RADIUS_MIN,
                "Радиус лагеря начинается с 1: меньшая зона не прикроет даже одного духа");
        assertEquals(32, CatalogLimits.CAMP_RADIUS_MAX,
                "Радиус лагеря ограничен 32: большая зона расползается дальше обороны");
        assertEquals(16, CatalogLimits.CAMP_RADIUS_DEFAULT,
                "Радиус лагеря по умолчанию 16: как радиус обороны и CampTuning.CAMP_RADIUS");
    }
}
