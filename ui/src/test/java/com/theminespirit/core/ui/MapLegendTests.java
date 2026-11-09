package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MapLegendTests {
    private static final MapPaint.Font FONT = new MapPaint.Font(text -> text.length() * 6, 9);
    private static final MapLegend.Text TEXT =
            (key, args) -> key.substring(key.lastIndexOf('.') + 1) + (args.length == 0 ? "" : args[0]);
    private static final MapViewport MAP = new MapViewport(0, 0, 1, 0, 0, 200, 120);

    @Test
    void rows_MissingSites_CrossedAndNotFound() {
        MapLegend legend = MapLegend.initial().withQuarries(List.of(MapQuarry.unset("dirt"), MapQuarry.unset("sand")));

        List<MapLegend.Row> rows = legend.rows();

        assertEquals(3, rows.size(), "В легенде три строки: земля, песок и тропинки");
        MapLegend.Row dirt = rows.get(0);
        assertTrue(dirt.crossed(), "Места нет — значок перечёркнут, а не пустой квадрат");
        assertFalse(dirt.found(), "Места нет — строка не притворяется найденной");
        assertEquals(MapLegend.KEY_MISSING, dirt.noteKey(), "Подпись места без точки — «не найдено»");
    }

    @Test
    void rows_LowSite_ShowsPercent() {
        MapLegend legend = MapLegend.initial().withQuarries(List.of(MapQuarry.of("dirt", "low", 7)));

        MapLegend.Row dirt = legend.rows().get(0);

        assertFalse(dirt.crossed(), "Место есть — значок не перечёркнут");
        assertTrue(dirt.showsPercent(), "Мало ресурса — легенда показывает остаток");
        assertEquals(7, dirt.percent(), "Остаток места добычи — 7%");
        assertEquals(MapLegend.KEY_LOW, dirt.noteKey(), "Мало ресурса — подпись «мало (N%)»");
    }

    @Test
    void rows_PathsOff_DimmedAndCrossed() {
        MapLegend legend = MapLegend.initial().toggledPaths();

        MapLegend.Row paths = legend.rows().get(2);

        assertFalse(legend.pathsVisible(), "Клик по строке тропинок выключает сеть тропинок");
        assertTrue(paths.dimmed(), "Выключенные тропинки серые: их не видно на карте");
        assertTrue(paths.crossed(), "Выключенные тропинки зачёркнуты: понятно, что сеть скрыта");
        assertEquals(
                MapLegend.Kind.PATHS,
                paths.kind(),
                "Строка тропинок остаётся в легенде: по ней же сеть включают обратно");
    }

    @Test
    void frame_FarOffCorner_StaysInsideMap() {
        MapLegend legend = new MapLegend(true, 9999, -9999, true, MapLegend.Site.missing(), MapLegend.Site.missing());

        MapLegend.Frame frame = legend.frame(MAP, FONT, TEXT);

        assertTrue(frame.x() >= MAP.left(), "Окно легенды не выходит за левый край карты");
        assertTrue(frame.y() >= MAP.top(), "Окно легенды не выходит за верхний край карты");
        assertTrue(frame.x() + frame.width() <= MAP.left() + MAP.width(), "Окно не выходит за правый край карты");
        assertTrue(frame.y() + frame.height() <= MAP.top() + MAP.height(), "Окно не выходит за нижний край карты");
    }

    @Test
    void frame_DefaultPosition_TopRightInsideMap() {
        MapLegend legend = MapLegend.initial();

        MapLegend.Frame frame = legend.frame(MAP, FONT, TEXT);

        assertEquals(
                MAP.left() + MAP.width() - MapLegend.PAD,
                frame.x() + frame.width(),
                "По умолчанию легенда прижата к правому краю карты, а не закрывает план");
        assertEquals(MAP.top() + MapLegend.PAD, frame.y(), "По умолчанию легенда прижата к верху карты");
    }

    @Test
    void moved_BeyondEdge_ClampedInsideMap() {
        MapLegend legend = MapLegend.initial().moved(-9999, -9999, MAP, FONT, TEXT);

        MapLegend.Frame frame = legend.frame(MAP, FONT, TEXT);

        assertEquals(MAP.left(), frame.x(), "Перетаскивание за левый край прижимает окно к карте");
        assertEquals(MAP.top(), frame.y(), "Перетаскивание за верхний край прижимает окно к карте");
    }

    @Test
    void frame_SmallMap_WindowNotBiggerThanMap() {
        MapViewport small = new MapViewport(0, 0, 1, 5, 5, 40, 30);

        MapLegend.Frame frame = MapLegend.initial().frame(small, FONT, TEXT);

        assertTrue(frame.width() <= small.width(), "Окно не шире карты: иначе легенда закрывает весь экран");
        assertTrue(frame.height() <= small.height(), "Окно не выше карты");
        assertTrue(frame.x() + frame.width() <= small.left() + small.width(), "Окно прижато к правому краю");
    }

    @Test
    void closeAndPaths_Clicks_HitTheirRows() {
        MapLegend legend = MapLegend.initial();
        MapLegend.Frame frame = legend.frame(MAP, FONT, TEXT);

        assertTrue(
                frame.closeAt(frame.closeX() + 1, frame.closeY() + 1),
                "Крестик закрытия попадает в свою клетку: легенду можно закрыть");
        MapLegend.Frame.RowBox paths = frame.rows().get(2);
        assertEquals(
                MapLegend.Kind.PATHS,
                frame.rowAt(paths.x1() + 1, paths.y1() + 1).kind(),
                "Клик по строке тропинок возвращает именно её: сеть скрывают и показывают");
    }

    @Test
    void withQuarries_UnknownResource_Ignored() {
        MapLegend legend = MapLegend.initial().withQuarries(List.of(MapQuarry.of("snow", "active", 50)));

        assertFalse(legend.rows().get(0).found(), "Снег не подменяет место добычи земли");
        assertFalse(legend.rows().get(1).found(), "Снег не подменяет место добычи песка");
    }
}
