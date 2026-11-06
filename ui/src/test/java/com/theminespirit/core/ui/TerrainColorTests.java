package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TerrainColorTests {
    @Test
    void argb_Water_BlueDominates() {
        int water = TerrainColor.argb(12);
        assertEquals(0xFF3737DC, water, "Вода — синий цвет MapColor с обычной яркостью карты");
        assertTrue(blue(water) > red(water) && blue(water) > green(water), "У воды синий старше остальных");
    }

    @Test
    void argb_GrassAndLeaves_GreenDominates() {
        for (int id : new int[] {1, 7}) {
            int color = TerrainColor.argb(id);
            assertEquals(0xFF, color >>> 24, "Цвет колонки непрозрачен, иначе карта просвечивает фоном");
            assertTrue(green(color) > red(color) && green(color) > blue(color), "Трава и листва зелёные, id " + id);
        }
    }

    @Test
    void argb_NoneAndOutOfRange_Background() {
        assertEquals(TerrainColor.NONE, TerrainColor.argb(0), "Пустая колонка — цвет фона карты, а не чёрная дыра");
        assertEquals(TerrainColor.NONE, TerrainColor.argb(-1), "Отрицательный id — фон");
        assertEquals(TerrainColor.NONE, TerrainColor.argb(64), "Палитра карты кончается на 63");
    }

    private static int red(int argb) {
        return argb >> 16 & 0xFF;
    }

    private static int green(int argb) {
        return argb >> 8 & 0xFF;
    }

    private static int blue(int argb) {
        return argb & 0xFF;
    }
}
