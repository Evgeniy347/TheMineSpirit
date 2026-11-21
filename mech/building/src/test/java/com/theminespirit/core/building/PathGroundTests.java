package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PathGroundTests {
    @Test
    void openAbove_Air_IsOpenForPath() {
        assertTrue(
                PathGround.openAbove("minecraft:air"),
                "Дорожку топчут только по земле под открытым небом: воздух над клеткой — открыто"
                        + " Иначе тест openAbove_Air_IsOpenForPath перестанет ловить эту поломку. При смене openAbove_Air_IsOpenForPath проверь это утверждение.");
    }

    @Test
    void raisedPath_AirAboveGrass_LetsShovelTrampleTheGrassBelow() {
        assertTrue(
                PathGround.raisedPath("minecraft:air", "minecraft:grass_block"),
                "Если клетка схемы оказалась в воздухе над травой, лопата топчет траву под ней: иначе на неровной "
                        + "земле тропа встаёт блоком над поверхностью"
                        + " При смене raisedPath_AirAboveGrass_LetsShovelTrampleTheGrassBelow проверь это утверждение.");
    }

    @Test
    void raisedPath_StoneAboveGrass_IsNotPath() {
        assertFalse(
                PathGround.raisedPath("minecraft:stone", "minecraft:grass_block"),
                "Занятую клетку дорожкой не заменяют"
                        + " Иначе тест raisedPath_StoneAboveGrass_IsNotPath перестанет ловить эту поломку. При смене raisedPath_StoneAboveGrass_IsNotPath проверь это утверждение.");
    }

    @Test
    void airCell_EmptyOrFilled_IsDetected() {
        assertTrue(
                PathGround.airCell("minecraft:air"),
                "пустая клетка пути — воздух"
                        + " Иначе тест airCell_EmptyOrFilled_IsDetected перестанет ловить эту поломку. При смене airCell_EmptyOrFilled_IsDetected проверь это утверждение.");
        assertFalse(
                PathGround.airCell("minecraft:grass_block"),
                "трава — не пустая клетка"
                        + " Иначе тест airCell_EmptyOrFilled_IsDetected перестанет ловить эту поломку. При смене airCell_EmptyOrFilled_IsDetected проверь это утверждение.");
    }

    @Test
    void openAbove_Stone_IsNotOpenForPath() {
        assertFalse(
                PathGround.openAbove("minecraft:stone"),
                "Подземная земля под камнем не годится для тропинки: лопата не сделает дорожку под потолком"
                        + " Иначе тест openAbove_Stone_IsNotOpenForPath перестанет ловить эту поломку. При смене openAbove_Stone_IsNotOpenForPath проверь это утверждение.");
    }
}
