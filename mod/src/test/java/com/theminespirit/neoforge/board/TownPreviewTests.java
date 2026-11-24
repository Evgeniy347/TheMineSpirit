package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TownPreviewTests {
    private static final int WHITE = Color.WHITE.getRGB();

    @Test
    void legend_WithSchemes_PaintsPanelOverTerrain() {
        BufferedImage image = blank();

        paint(image, Map.of("wall_3_straight", 1, "gate_3", 2));

        assertNotEquals(
                WHITE,
                image.getRGB(6, 22),
                "Легенда фрагментов стены обязана затемнять клетку под подписями: иначе ID схем на карте не видно "
                        + "Иначе тест legend_WithSchemes_PaintsPanelOverTerrain перестанет ловить эту поломку."
                        + " При смене legend_WithSchemes_PaintsPanelOverTerrain проверь это утверждение.");
    }

    @Test
    void legend_WithoutSchemes_LeavesTerrainUntouched() {
        BufferedImage image = blank();

        paint(image, new LinkedHashMap<>());

        assertEquals(
                WHITE,
                image.getRGB(6, 22),
                "Без фрагментов стены легенда не рисуется и не закрывает карту: иначе пустая стена оставляет "
                        + "чёрный прямоугольник"
                        + " Иначе тест legend_WithoutSchemes_LeavesTerrainUntouched перестанет ловить эту поломку."
                        + " При смене legend_WithoutSchemes_LeavesTerrainUntouched проверь это утверждение.");
    }

    private static BufferedImage blank() {
        BufferedImage image = new BufferedImage(240, 160, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 240, 160);
        graphics.dispose();
        return image;
    }

    private static void paint(BufferedImage image, Map<String, Integer> schemes) {
        Graphics2D graphics = image.createGraphics();
        TownPreview.legend(graphics, schemes, 2);
        graphics.dispose();
    }
}
