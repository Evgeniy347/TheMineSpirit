package com.theminespirit.core.building;

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
    void covers_OtherBlocks_Rejected() {
        assertFalse(EarthCover.covers("minecraft:stone", "minecraft:dirt"), "камень на земляной клетке — не земля");
        assertFalse(
                EarthCover.covers("minecraft:grass_block", "minecraft:dirt_path"),
                "тропа этапов 2–3 дёрном не заменяется");
    }
}
