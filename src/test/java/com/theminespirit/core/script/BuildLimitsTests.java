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
}
