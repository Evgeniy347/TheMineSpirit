package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class UnloadedSpotsTests {
    @Test
    void line_ManySpots_ShowsCountAndFirstFive() {
        List<BlockCoord> spots = new ArrayList<>();
        for (int index = 0; index < 503; index++) {
            spots.add(new BlockCoord(index, 64, -500));
        }

        String line = UnloadedSpots.line(spots);

        assertTrue(
                line.startsWith("контейнеров вне загруженных чанков: 503 (первые 5: "),
                "Стартовый всплеск — одна сводка с числом точек, а не 503 строки на каждый сундук");
        assertTrue(
                line.contains("0,64,-500") && line.contains("4,64,-500"),
                "В сводку входят первые пять точек: по ним видно, где искать незагруженные чанки");
        assertFalse(line.contains("5,64,-500"), "Шестая точка в сводку не влезает: список ограничен пятью");
    }

    @Test
    void line_NoSpots_IsEmpty() {
        assertEquals(
                "",
                UnloadedSpots.line(List.of()),
                "Когда все контейнеры загружены, писать нечего: пустая строка не засоряет лог");
    }

    @Test
    void line_FewerThanFive_ListsAll() {
        String line = UnloadedSpots.line(List.of(new BlockCoord(1, 70, 2), new BlockCoord(3, 71, 4)));

        assertTrue(
                line.contains("(первые 2: 1,70,2, 3,71,4)"),
                "Если точек меньше пяти, перечисляются все: сводка обязана называть их без выдумок");
    }
}
