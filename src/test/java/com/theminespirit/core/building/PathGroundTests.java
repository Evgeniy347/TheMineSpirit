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
    void raisedPath_AirAboveGrass_LetsShovelTrampleTheGrassBelow() {
        assertTrue(
                PathGround.raisedPath("minecraft:air", "minecraft:grass_block"),
                "Если клетка схемы оказалась в воздухе над травой, лопата топчет траву под ней: иначе на неровной "
                        + "земле тропа встаёт блоком над поверхностью");
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
