package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TerrainColorTests {
    @Test
    void argb_Water_BlueDominates() {
        int water = TerrainColor.argb(12);
        assertEquals(
                0xFF3737DC,
                water,
                "Вода — синий цвет MapColor с обычной яркостью карты"
                        + " Иначе тест argb_Water_BlueDominates перестанет ловить эту поломку. При смене argb_Water_BlueDominates проверь это утверждение.");
        assertTrue(
                blue(water) > red(water) && blue(water) > green(water),
                "У воды синий старше остальных"
                        + " Иначе тест argb_Water_BlueDominates перестанет ловить эту поломку. При смене argb_Water_BlueDominates проверь это утверждение.");
    }

    @Test
    void argb_GrassAndLeaves_GreenDominates() {
        for (int id : new int[] {1, 7}) {
            int color = TerrainColor.argb(id);
            assertEquals(
                    0xFF,
                    color >>> 24,
                    "Цвет колонки непрозрачен, иначе карта просвечивает фоном"
                            + " При смене argb_GrassAndLeaves_GreenDominates проверь это утверждение.");
            assertTrue(
                    green(color) > red(color) && green(color) > blue(color),
                    "Трава и листва зелёные, id " + id
                            + " Иначе тест argb_GrassAndLeaves_GreenDominates перестанет ловить эту поломку. При смене argb_GrassAndLeaves_GreenDominates проверь это утверждение.");
        }
    }

    @Test
    void argb_NoneAndOutOfRange_Background() {
        assertEquals(
                TerrainColor.NONE,
                TerrainColor.argb(0),
                "Пустая колонка — цвет фона карты, а не чёрная дыра"
                        + " Иначе тест argb_NoneAndOutOfRange_Background перестанет ловить эту поломку. При смене argb_NoneAndOutOfRange_Background проверь это утверждение.");
        assertEquals(
                TerrainColor.NONE,
                TerrainColor.argb(-1),
                "Отрицательный id — фон"
                        + " Иначе тест argb_NoneAndOutOfRange_Background перестанет ловить эту поломку. При смене argb_NoneAndOutOfRange_Background проверь это утверждение.");
        assertEquals(
                TerrainColor.NONE,
                TerrainColor.argb(64),
                "Палитра карты кончается на 63"
                        + " Иначе тест argb_NoneAndOutOfRange_Background перестанет ловить эту поломку. При смене argb_NoneAndOutOfRange_Background проверь это утверждение.");
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
