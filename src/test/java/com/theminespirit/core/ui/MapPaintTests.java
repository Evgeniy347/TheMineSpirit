package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MapPaintTests {
    private static final MapPaint.Font FONT = new MapPaint.Font(text -> 6 * text.length(), 9);
    private static final TownMapView.MapRect WIDE =
            new TownMapView.MapRect(TownMapView.Kind.LOT, 0, 0, 39, 9, "Склад", 50, false);
    private static final TownMapView.MapRect NARROW =
            new TownMapView.MapRect(TownMapView.Kind.LOT, 0, 12, 1, 13, "Колодец", 100, false);
    private static final TownMapView.MapRect ROAD =
            new TownMapView.MapRect(TownMapView.Kind.STREET_MAIN, 0, 10, 39, 11, "", 0, false);
    private static final TownMapView.MapLine FENCE = new TownMapView.MapLine(
            TownMapView.LineKind.CONTOUR,
            List.of(
                    new TownMapView.MapPoint(-1, -1), new TownMapView.MapPoint(40, -1),
                    new TownMapView.MapPoint(40, 14), new TownMapView.MapPoint(-1, 14)),
            List.of(new TownMapView.MapPoint(20, -1)));
    private static final TownMapView VIEW = new TownMapView(List.of(ROAD, WIDE, NARROW), List.of(FENCE));
    private static final MapViewport VIEWPORT = new MapViewport(20, 7, 4, 0, 0, 200, 80);

    @Test
    void of_WideLot_LabelInsideItsBox() {
        List<MapPaint.Text> texts = texts(MapPaint.of(VIEW, VIEWPORT, Optional.empty(), FONT));
        assertEquals(1, texts.size(), "Подпись только у участка, где она влезает: " + texts);
        MapPaint.Text text = texts.get(0);
        assertEquals("Склад", text.text(), "Подпись — название участка");
        assertTrue(
                text.x() >= VIEWPORT.screenX(0)
                        && text.x() + 30 <= VIEWPORT.screenX(40)
                        && text.y() + 9 <= VIEWPORT.screenY(10),
                "Подпись не вылезает за участок: " + text);
    }

    @Test
    void of_Hovered_BorderHighlighted() {
        List<MapPaint.Shape> plain = MapPaint.of(VIEW, VIEWPORT, Optional.empty(), FONT);
        List<MapPaint.Shape> hovered = MapPaint.of(VIEW, VIEWPORT, Optional.of(WIDE), FONT);
        assertEquals(0, count(plain, MapStyle.HOVER_BORDER), "Без наведения рамки подсветки нет");
        assertEquals(1, count(hovered, MapStyle.HOVER_BORDER), "Наведённый участок — светлая рамка");
    }

    @Test
    void of_FenceWithGate_LinesThenGatesThenLabelsOnTop() {
        List<MapPaint.Shape> shapes = MapPaint.of(VIEW, VIEWPORT, Optional.empty(), FONT);
        assertEquals(MapStyle.BACKGROUND, ((MapPaint.Fill) shapes.get(0)).color(), "Сначала фон окна");
        assertEquals(4, count(shapes, MapStyle.CONTOUR), "Замкнутый контур из 4 точек — 4 отрезка");
        assertEquals(1, count(shapes, MapStyle.GATE), "Калитка — отдельный квадрат");
        assertTrue(shapes.get(shapes.size() - 1) instanceof MapPaint.Text, "Подписи рисуются последними");
    }

    @Test
    void of_Terrain_BackgroundThenTerrainThenSemiTransparentPlan() {
        TerrainRaster terrain = TerrainRaster.of(List.of(tile(0, 0, 12)));
        List<MapPaint.Shape> shapes = MapPaint.of(VIEW, Optional.of(terrain), VIEWPORT, Optional.empty(), FONT);
        assertEquals(MapStyle.BACKGROUND, ((MapPaint.Fill) shapes.get(0)).color(), "Сначала фон окна");
        MapPaint.Raster raster = (MapPaint.Raster) shapes.get(1);
        assertTrue(raster.x2() > raster.x1() && raster.y2() > raster.y1(), "Слой местности — первой картинкой на фоне");
        assertTrue(shapes.indexOf(raster) < shapes.size() - 1, "План рисуется поверх местности");
        assertTrue((MapStyle.fill(WIDE) >>> 24) < 0xFF, "Участок полупрозрачен, чтобы видеть рельеф под ним");
        assertTrue((MapStyle.fill(ROAD) >>> 24) < 0xFF, "Улица полупрозрачна, чтобы видеть рельеф под ней");
    }

    @Test
    void hint_DamagedLot_IntactAndRemainingPercent() {
        TownMapView.MapRect damaged = new TownMapView.MapRect(TownMapView.Kind.LOT, 0, 0, 4, 4, "Хижина", 70, true);
        assertEquals(
                new MapHint("Хижина", MapHint.KEY_DAMAGED, List.of(70, 30)),
                MapHint.of(damaged),
                "Повреждённая постройка: цела на 70%, до восстановления 30%");
        assertEquals(
                new MapHint("Склад", MapHint.KEY_LOT, List.of(50)), MapHint.of(WIDE), "Целая постройка: готово 50%");
    }

    @Test
    void tabs_TopRight_SideBySideInsideScreen() {
        BoardLayout.Rect board = ScreenTabs.rect(427, ScreenTabs.Tab.BOARD);
        BoardLayout.Rect plan = ScreenTabs.rect(427, ScreenTabs.Tab.PLAN);
        assertEquals(board.x() + board.width() + ScreenTabs.GAP, plan.x(), "Вкладки рядом, «План» правее доски");
        assertEquals(427 - BoardLayout.MARGIN, plan.x() + plan.width(), "Вкладки прижаты к правому краю");
    }

    private static TerrainTile tile(int chunkX, int chunkZ, int id) {
        byte[] ids = new byte[TerrainTile.COLUMNS];
        Arrays.fill(ids, (byte) id);
        return new TerrainTile(chunkX, chunkZ, ids);
    }

    private static List<MapPaint.Text> texts(List<MapPaint.Shape> shapes) {
        return shapes.stream()
                .filter(MapPaint.Text.class::isInstance)
                .map(MapPaint.Text.class::cast)
                .toList();
    }

    private static long count(List<MapPaint.Shape> shapes, int color) {
        return shapes.stream()
                .filter(shape -> shape instanceof MapPaint.Fill fill && fill.color() == color)
                .count();
    }
}
