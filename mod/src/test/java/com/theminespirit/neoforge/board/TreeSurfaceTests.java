package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TreeSurfaceTests {
    @Test
    void skipWood_TopIsLog_SkipsToGroundAbove() {
        assertEquals(
                66,
                TreeSurface.skipWood(74, 32, y -> y >= 66),
                "Ствол до 74 не считается поверхностью: дух на верхушке дерева не должен считаться вышедшим, "
                        + "иначе спасение снова выбирает мост по воздуху. При смене "
                        + "skipWood_TopIsLog_SkipsToGroundAbove проверь шаг вниз по брёвнам.");
    }

    @Test
    void skipWood_NoLog_KeepsHeightmapTop() {
        assertEquals(
                75,
                TreeSurface.skipWood(74, 32, y -> false),
                "Опора не из дерева остаётся поверхностью как есть: иначе обычный выход из ямы на землю "
                        + "перестанет распознаваться. При смене skipWood_NoLog_KeepsHeightmapTop проверь "
                        + "возврат для пустого предиката.");
    }

    @Test
    void skipWood_DepthLimit_StopsScan() {
        assertEquals(
                73,
                TreeSurface.skipWood(74, 2, y -> true),
                "Глубина поиска ограничена: иначе на ошибочном определении дерева проверка спускается до "
                        + "бедрока каждый тик. При смене skipWood_DepthLimit_StopsScan проверь WOOD_SCAN_DEPTH.");
    }
}
