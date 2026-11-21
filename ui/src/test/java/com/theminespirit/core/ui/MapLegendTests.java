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

        assertEquals(
                3,
                rows.size(),
                "В легенде три строки: земля, песок и тропинки"
                        + " Иначе тест rows_MissingSites_CrossedAndNotFound перестанет ловить эту поломку. При смене rows_MissingSites_CrossedAndNotFound проверь это утверждение.");
        MapLegend.Row dirt = rows.get(0);
        assertTrue(
                dirt.crossed(),
                "Места нет — значок перечёркнут, а не пустой квадрат"
                        + " Иначе тест rows_MissingSites_CrossedAndNotFound перестанет ловить эту поломку. При смене rows_MissingSites_CrossedAndNotFound проверь это утверждение.");
        assertFalse(
                dirt.found(),
                "Места нет — строка не притворяется найденной"
                        + " Иначе тест rows_MissingSites_CrossedAndNotFound перестанет ловить эту поломку. При смене rows_MissingSites_CrossedAndNotFound проверь это утверждение.");
        assertEquals(
                MapLegend.KEY_MISSING,
                dirt.noteKey(),
                "Подпись места без точки — «не найдено»"
                        + " Иначе тест rows_MissingSites_CrossedAndNotFound перестанет ловить эту поломку. При смене rows_MissingSites_CrossedAndNotFound проверь это утверждение.");
    }

    @Test
    void rows_LowSite_ShowsPercent() {
        MapLegend legend = MapLegend.initial().withQuarries(List.of(MapQuarry.of("dirt", "low", 7)));

        MapLegend.Row dirt = legend.rows().get(0);

        assertFalse(
                dirt.crossed(),
                "Место есть — значок не перечёркнут"
                        + " Иначе тест rows_LowSite_ShowsPercent перестанет ловить эту поломку. При смене rows_LowSite_ShowsPercent проверь это утверждение.");
        assertTrue(
                dirt.showsPercent(),
                "Мало ресурса — легенда показывает остаток"
                        + " Иначе тест rows_LowSite_ShowsPercent перестанет ловить эту поломку. При смене rows_LowSite_ShowsPercent проверь это утверждение.");
        assertEquals(
                7,
                dirt.percent(),
                "Остаток места добычи — 7%"
                        + " Иначе тест rows_LowSite_ShowsPercent перестанет ловить эту поломку. При смене rows_LowSite_ShowsPercent проверь это утверждение.");
        assertEquals(
                MapLegend.KEY_LOW,
                dirt.noteKey(),
                "Мало ресурса — подпись «мало (N%)»"
                        + " Иначе тест rows_LowSite_ShowsPercent перестанет ловить эту поломку. При смене rows_LowSite_ShowsPercent проверь это утверждение.");
    }

    @Test
    void rows_PathsOff_DimmedAndCrossed() {
        MapLegend legend = MapLegend.initial().toggledPaths();

        MapLegend.Row paths = legend.rows().get(2);

        assertFalse(
                legend.pathsVisible(),
                "Клик по строке тропинок выключает сеть тропинок"
                        + " Иначе тест rows_PathsOff_DimmedAndCrossed перестанет ловить эту поломку. При смене rows_PathsOff_DimmedAndCrossed проверь это утверждение.");
        assertTrue(
                paths.dimmed(),
                "Выключенные тропинки серые: их не видно на карте"
                        + " Иначе тест rows_PathsOff_DimmedAndCrossed перестанет ловить эту поломку. При смене rows_PathsOff_DimmedAndCrossed проверь это утверждение.");
        assertTrue(
                paths.crossed(),
                "Выключенные тропинки зачёркнуты: понятно, что сеть скрыта"
                        + " Иначе тест rows_PathsOff_DimmedAndCrossed перестанет ловить эту поломку. При смене rows_PathsOff_DimmedAndCrossed проверь это утверждение.");
        assertEquals(
                MapLegend.Kind.PATHS,
                paths.kind(),
                "Строка тропинок остаётся в легенде: по ней же сеть включают обратно"
                        + " Иначе тест rows_PathsOff_DimmedAndCrossed перестанет ловить эту поломку. При смене rows_PathsOff_DimmedAndCrossed проверь это утверждение.");
    }

    @Test
    void frame_FarOffCorner_StaysInsideMap() {
        MapLegend legend = new MapLegend(true, 9999, -9999, true, MapLegend.Site.missing(), MapLegend.Site.missing());

        MapLegend.Frame frame = legend.frame(MAP, FONT, TEXT);

        assertTrue(
                frame.x() >= MAP.left(),
                "Окно легенды не выходит за левый край карты"
                        + " Иначе тест frame_FarOffCorner_StaysInsideMap перестанет ловить эту поломку. При смене frame_FarOffCorner_StaysInsideMap проверь это утверждение.");
        assertTrue(
                frame.y() >= MAP.top(),
                "Окно легенды не выходит за верхний край карты"
                        + " Иначе тест frame_FarOffCorner_StaysInsideMap перестанет ловить эту поломку. При смене frame_FarOffCorner_StaysInsideMap проверь это утверждение.");
        assertTrue(
                frame.x() + frame.width() <= MAP.left() + MAP.width(),
                "Окно не выходит за правый край карты"
                        + " Иначе тест frame_FarOffCorner_StaysInsideMap перестанет ловить эту поломку. При смене frame_FarOffCorner_StaysInsideMap проверь это утверждение.");
        assertTrue(
                frame.y() + frame.height() <= MAP.top() + MAP.height(),
                "Окно не выходит за нижний край карты"
                        + " Иначе тест frame_FarOffCorner_StaysInsideMap перестанет ловить эту поломку. При смене frame_FarOffCorner_StaysInsideMap проверь это утверждение.");
    }

    @Test
    void frame_DefaultPosition_TopRightInsideMap() {
        MapLegend legend = MapLegend.initial();

        MapLegend.Frame frame = legend.frame(MAP, FONT, TEXT);

        assertEquals(
                MAP.left() + MAP.width() - MapLegend.PAD,
                frame.x() + frame.width(),
                "По умолчанию легенда прижата к правому краю карты, а не закрывает план"
                        + " Иначе тест frame_DefaultPosition_TopRightInsideMap перестанет ловить эту поломку. При смене frame_DefaultPosition_TopRightInsideMap проверь это утверждение.");
        assertEquals(
                MAP.top() + MapLegend.PAD,
                frame.y(),
                "По умолчанию легенда прижата к верху карты"
                        + " Иначе тест frame_DefaultPosition_TopRightInsideMap перестанет ловить эту поломку. При смене frame_DefaultPosition_TopRightInsideMap проверь это утверждение.");
    }

    @Test
    void moved_BeyondEdge_ClampedInsideMap() {
        MapLegend legend = MapLegend.initial().moved(-9999, -9999, MAP, FONT, TEXT);

        MapLegend.Frame frame = legend.frame(MAP, FONT, TEXT);

        assertEquals(
                MAP.left(),
                frame.x(),
                "Перетаскивание за левый край прижимает окно к карте"
                        + " Иначе тест moved_BeyondEdge_ClampedInsideMap перестанет ловить эту поломку. При смене moved_BeyondEdge_ClampedInsideMap проверь это утверждение.");
        assertEquals(
                MAP.top(),
                frame.y(),
                "Перетаскивание за верхний край прижимает окно к карте"
                        + " Иначе тест moved_BeyondEdge_ClampedInsideMap перестанет ловить эту поломку. При смене moved_BeyondEdge_ClampedInsideMap проверь это утверждение.");
    }

    @Test
    void frame_SmallMap_WindowNotBiggerThanMap() {
        MapViewport small = new MapViewport(0, 0, 1, 5, 5, 40, 30);

        MapLegend.Frame frame = MapLegend.initial().frame(small, FONT, TEXT);

        assertTrue(
                frame.width() <= small.width(),
                "Окно не шире карты: иначе легенда закрывает весь экран"
                        + " При смене frame_SmallMap_WindowNotBiggerThanMap проверь это утверждение.");
        assertTrue(
                frame.height() <= small.height(),
                "Окно не выше карты"
                        + " Иначе тест frame_SmallMap_WindowNotBiggerThanMap перестанет ловить эту поломку. При смене frame_SmallMap_WindowNotBiggerThanMap проверь это утверждение.");
        assertTrue(
                frame.x() + frame.width() <= small.left() + small.width(),
                "Окно прижато к правому краю"
                        + " Иначе тест frame_SmallMap_WindowNotBiggerThanMap перестанет ловить эту поломку. При смене frame_SmallMap_WindowNotBiggerThanMap проверь это утверждение.");
    }

    @Test
    void closeAndPaths_Clicks_HitTheirRows() {
        MapLegend legend = MapLegend.initial();
        MapLegend.Frame frame = legend.frame(MAP, FONT, TEXT);

        assertTrue(
                frame.closeAt(frame.closeX() + 1, frame.closeY() + 1),
                "Крестик закрытия попадает в свою клетку: легенду можно закрыть"
                        + " Иначе тест closeAndPaths_Clicks_HitTheirRows перестанет ловить эту поломку. При смене closeAndPaths_Clicks_HitTheirRows проверь это утверждение.");
        MapLegend.Frame.RowBox paths = frame.rows().get(2);
        assertEquals(
                MapLegend.Kind.PATHS,
                frame.rowAt(paths.x1() + 1, paths.y1() + 1).kind(),
                "Клик по строке тропинок возвращает именно её: сеть скрывают и показывают"
                        + " Иначе тест closeAndPaths_Clicks_HitTheirRows перестанет ловить эту поломку. При смене closeAndPaths_Clicks_HitTheirRows проверь это утверждение.");
    }

    @Test
    void withQuarries_UnknownResource_Ignored() {
        MapLegend legend = MapLegend.initial().withQuarries(List.of(MapQuarry.of("snow", "active", 50)));

        assertFalse(
                legend.rows().get(0).found(),
                "Снег не подменяет место добычи земли"
                        + " Иначе тест withQuarries_UnknownResource_Ignored перестанет ловить эту поломку. При смене withQuarries_UnknownResource_Ignored проверь это утверждение.");
        assertFalse(
                legend.rows().get(1).found(),
                "Снег не подменяет место добычи песка"
                        + " Иначе тест withQuarries_UnknownResource_Ignored перестанет ловить эту поломку. При смене withQuarries_UnknownResource_Ignored проверь это утверждение.");
    }
}
