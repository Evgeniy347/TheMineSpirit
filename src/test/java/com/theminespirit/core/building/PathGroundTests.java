package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PathGroundTests {
    @Test
    void openAbove_Air_IsOpenForPath() {
        assertTrue(
                PathGround.openAbove("minecraft:air"),
                "Дорожку топчут только по земле под открытым небом: воздух над клеткой — открыто");
    }

    @Test
    void raisedPath_AirAboveGrass_IsSupportedPath() {
        assertTrue(
                PathGround.raisedPath("minecraft:air", "minecraft:grass_block"),
                "Дорожка сада ставится на траву, даже если клетка схемы в воздухе: иначе BuildSchematic не может "
                        + "положить тропинку на ровную поверхность");
    }

    @Test
    void raisedPath_StoneAboveGrass_IsNotPath() {
        assertFalse(
                PathGround.raisedPath("minecraft:stone", "minecraft:grass_block"),
                "Занятую клетку дорожкой не заменяют");
    }

    @Test
    void openAbove_Stone_IsNotOpenForPath() {
        assertFalse(
                PathGround.openAbove("minecraft:stone"),
                "Подземная земля под камнем не годится для тропинки: лопата не сделает дорожку под потолком");
    }
}
