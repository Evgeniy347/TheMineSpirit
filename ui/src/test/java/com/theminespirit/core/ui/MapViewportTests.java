package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MapViewportTests {
    private static final TownMapView.Bounds BOUNDS = new TownMapView.Bounds(100, -50, 199, 49);

    @Test
    void fit_Bounds_WholePlanInsideWindow() {
        MapViewport viewport = MapViewport.fit(BOUNDS, 10, 20, 300, 200);

        assertTrue(
                viewport.inside(viewport.screenX(100), viewport.screenY(-50))
                        && viewport.inside(viewport.screenX(199.9), viewport.screenY(49.9)),
                "При открытии весь план виден в окне карты: " + viewport
                        + " Иначе тест fit_Bounds_WholePlanInsideWindow перестанет ловить эту поломку. При смене fit_Bounds_WholePlanInsideWindow проверь это утверждение.");
    }

    @Test
    void zoom_Wheel_PointUnderCursorStays() {
        MapViewport viewport = MapViewport.fit(BOUNDS, 0, 0, 300, 200);
        double worldX = viewport.worldX(70);
        double worldZ = viewport.worldZ(40);

        MapViewport zoomed = viewport.zoom(70, 40, 2);

        assertEquals(
                worldX,
                zoomed.worldX(70),
                1e-9,
                "Колесо масштабирует вокруг курсора: точка под ним стоит"
                        + " Иначе тест zoom_Wheel_PointUnderCursorStays перестанет ловить эту поломку. При смене zoom_Wheel_PointUnderCursorStays проверь это утверждение.");
        assertEquals(
                worldZ,
                zoomed.worldZ(40),
                1e-9,
                "Колесо масштабирует вокруг курсора по z"
                        + " Иначе тест zoom_Wheel_PointUnderCursorStays перестанет ловить эту поломку. При смене zoom_Wheel_PointUnderCursorStays проверь это утверждение.");
        assertEquals(
                viewport.scale() * 1.5625,
                zoomed.scale(),
                1e-9,
                "Два щелчка колеса — масштаб ×1.25²"
                        + " Иначе тест zoom_Wheel_PointUnderCursorStays перестанет ловить эту поломку. При смене zoom_Wheel_PointUnderCursorStays проверь это утверждение.");
    }

    @Test
    void zoom_ManyClicks_ClampedToLimits() {
        MapViewport viewport = MapViewport.fit(BOUNDS, 0, 0, 300, 200);

        assertEquals(
                MapViewport.MAX_SCALE,
                viewport.zoom(10, 10, 50).scale(),
                1e-9,
                "Приближение ограничено"
                        + " Иначе тест zoom_ManyClicks_ClampedToLimits перестанет ловить эту поломку. При смене zoom_ManyClicks_ClampedToLimits проверь это утверждение.");
        assertEquals(
                MapViewport.MIN_SCALE,
                viewport.zoom(10, 10, -50).scale(),
                1e-9,
                "Отдаление ограничено"
                        + " Иначе тест zoom_ManyClicks_ClampedToLimits перестанет ловить эту поломку. При смене zoom_ManyClicks_ClampedToLimits проверь это утверждение.");
    }

    @Test
    void pan_DragRight_MapMovesWithCursor() {
        MapViewport viewport = MapViewport.fit(BOUNDS, 0, 0, 300, 200);
        double before = viewport.screenX(150);

        MapViewport moved = viewport.pan(30, -12);

        assertEquals(
                before + 30,
                moved.screenX(150),
                1e-9,
                "Зажатая ЛКМ тянет карту за курсором по x"
                        + " Иначе тест pan_DragRight_MapMovesWithCursor перестанет ловить эту поломку. При смене pan_DragRight_MapMovesWithCursor проверь это утверждение.");
        assertEquals(
                viewport.screenY(0) - 12,
                moved.screenY(0),
                1e-9,
                "Зажатая ЛКМ тянет карту по y"
                        + " Иначе тест pan_DragRight_MapMovesWithCursor перестанет ловить эту поломку. При смене pan_DragRight_MapMovesWithCursor проверь это утверждение.");
    }
}
