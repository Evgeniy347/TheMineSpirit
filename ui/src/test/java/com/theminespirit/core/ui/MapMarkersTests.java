package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MapMarkersTests {
    private static final MapViewport WINDOW = new MapViewport(0, 0, 2, 0, 0, 400, 300);
    private static final MapPaint.Font FONT = new MapPaint.Font(text -> text.length() * 6, 9);
    private static final MapMarker SPIRIT =
            new MapMarker(MapMarker.Kind.SPIRIT, "spirit-1", "theminespirit.board.case.build_house", 0, 0);
    private static final MapMarker PLAYER = new MapMarker(MapMarker.Kind.PLAYER, "Steve", "", 10, 10);

    @Test
    void layout_BlockCenter_DotAndLabelInsideWindow() {
        List<MapMarkers.Placed> placed = MapMarkers.layout(List.of(SPIRIT), WINDOW, FONT);
        MapMarkers.Placed marker = placed.get(0);
        assertEquals(
                List.of(199, 149),
                List.of(marker.dotX(), marker.dotY()),
                "Точка метки стоит в центре блока 0,0 при масштабе 2: иначе игрок видит духа не там, где он стоит");
        assertEquals(207, marker.labelX(), "Подпись стоит справа от точки с зазором");
        assertTrue(
                marker.labelX() + marker.labelWidth() <= WINDOW.left() + WINDOW.width(), "Подпись не вылезает за окно");
    }

    @Test
    void at_DotAndLabel_ReturnOwnMarker() {
        MapMarkers.Placed marker =
                MapMarkers.layout(List.of(SPIRIT), WINDOW, FONT).get(0);
        assertEquals(
                Optional.of(SPIRIT),
                MapMarkers.at(List.of(SPIRIT), WINDOW, FONT, marker.dotX() + 2, marker.dotY() + 2),
                "Курсор на точке — подсказка духа");
        assertEquals(
                Optional.of(SPIRIT),
                MapMarkers.at(List.of(SPIRIT), WINDOW, FONT, marker.labelX() + 2, marker.labelY() + 2),
                "Курсор на подписи имени — та же подсказка");
        assertEquals(
                Optional.empty(),
                MapMarkers.at(List.of(SPIRIT), WINDOW, FONT, marker.labelX() + 200, marker.labelY()),
                "Курсор мимо метки — без подсказки");
    }

    @Test
    void at_OutsideWindow_Empty() {
        assertEquals(
                Optional.empty(),
                MapMarkers.at(List.of(SPIRIT), WINDOW, FONT, 500, 500),
                "Метки вне окна карты не ловят курсор");
    }

    @Test
    void layout_TwoMarkersInOneBlock_LabelsDoNotOverlap() {
        MapMarker second = new MapMarker(MapMarker.Kind.SPIRIT, "spirit-2", "", 0, 0);
        List<MapMarkers.Placed> placed = MapMarkers.layout(List.of(SPIRIT, second), WINDOW, FONT);
        assertEquals(2, placed.size(), "Обе метки в кадре");
        assertNotEquals(
                placed.get(0).labelY(),
                placed.get(1).labelY(),
                "Подписи двух меток в одном блоке разводятся по вертикали: иначе имена не прочитать");
        assertTrue(
                placed.get(0).labelY() + FONT.lineHeight() <= placed.get(1).labelY()
                        || placed.get(1).labelY() + FONT.lineHeight()
                                <= placed.get(0).labelY(),
                "Подписи соседних меток не налезают друг на друга");
    }

    @Test
    void layout_MarkerNearRightEdge_LabelFlipsLeft() {
        MapMarker far = new MapMarker(MapMarker.Kind.PLAYER, "far", "", 98, 0);
        MapMarkers.Placed marker = MapMarkers.layout(List.of(far), WINDOW, FONT).get(0);
        assertTrue(
                marker.labelX() < marker.dotX(),
                "У правого края подпись переносится влево от точки, а не обрезается окном");
    }

    @Test
    void layout_MarkerOutsideWindow_Skipped() {
        MapMarker away = new MapMarker(MapMarker.Kind.PLAYER, "away", "", 500, 500);
        assertEquals(
                List.of(),
                MapMarkers.layout(List.of(away), WINDOW, FONT),
                "Метка вне окна не раскладывается: карта не рисует пустые подписи за краем");
    }

    @Test
    void paint_SpiritAndPlayer_DifferentDotColorsAndBothNames() {
        List<MapPaint.Shape> shapes = MapMarkers.paint(List.of(SPIRIT, PLAYER), WINDOW, Optional.of(PLAYER), FONT);
        List<Integer> dots = shapes.stream()
                .filter(MapPaint.Fill.class::isInstance)
                .map(MapPaint.Fill.class::cast)
                .map(MapPaint.Fill::color)
                .filter(color -> color == MapStyle.SPIRIT || color == MapStyle.PLAYER)
                .toList();
        assertEquals(
                List.of(MapStyle.SPIRIT, MapStyle.PLAYER),
                dots,
                "Дух и игрок — точки разного цвета: по карте видно, кто перед тобой");
        List<String> names = shapes.stream()
                .filter(MapPaint.Text.class::isInstance)
                .map(MapPaint.Text.class::cast)
                .map(MapPaint.Text::text)
                .toList();
        assertEquals(List.of("spirit-1", "Steve"), names, "У каждой метки подписано имя");
    }
}
