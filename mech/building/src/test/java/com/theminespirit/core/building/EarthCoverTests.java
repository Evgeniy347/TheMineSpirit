package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EarthCoverTests {
    @Test
    void covers_GrassOnDirtCell_Accepted() {
        assertTrue(
                EarthCover.covers("minecraft:grass_block", "minecraft:dirt"),
                "дёрн на земляной клетке схемы — та же земля: копать траву ради земли незачем");
    }

    @Test
    void covers_DirtOnGrassCell_Accepted() {
        assertTrue(
                EarthCover.covers("minecraft:dirt", "minecraft:grass_block"),
                "земля на травяной клетке плана — равноценная замена: дёрн как блок не добыть");
    }

    @Test
    void restoreBlock_Grass_ReplacedByDirt() {
        assertEquals(
                "dirt", EarthCover.restoreBlock("minecraft:grass_block"), "травяной покров восстанавливают землёй");
        assertEquals("minecraft:stone", EarthCover.restoreBlock("minecraft:stone"), "камень не подменяется");
    }

    @Test
    void covers_OtherBlocks_Rejected() {
        assertFalse(EarthCover.covers("minecraft:stone", "minecraft:dirt"), "камень на земляной клетке — не земля");
        assertFalse(
                EarthCover.covers("minecraft:grass_block", "minecraft:dirt_path"),
                "тропа этапов 2–3 дёрном не заменяется");
    }
}
