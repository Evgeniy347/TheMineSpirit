package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DigOutObstacleTests {
    @Test
    void obstacle_WaterInPassage_IsNotDug() {
        assertFalse(
                DigOutObstacle.obstacle(false, true),
                "В observe13 дух тонул в пещере с водой: вода в проходе — не блок для ломки, иначе лестница "
                        + "вверх падает с «в проходе жидкость» и дух не выходит");
    }

    @Test
    void obstacle_SolidCeiling_IsDug() {
        assertTrue(
                DigOutObstacle.obstacle(false, false),
                "Камень над водой остаётся препятствием: его надо копать, иначе лестница к небу не начнётся");
    }

    @Test
    void obstacle_OpenCell_IsPassable() {
        assertFalse(DigOutObstacle.obstacle(true, false), "Открытая клетка не требует ломки");
    }
}
