package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.TownFixtures;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TownMapViewTests {
    private static final TownPlan PLAN = TownFixtures.flatPlan();
    private static final TownMapView VIEW = TownMapView.of(TownSnapshot.of(
            PLAN,
            TownFixtures.flatSite(),
            Map.of("hut", new TownSnapshot.Progress(30, 40, true)),
            List.of(List.of(new BlockCoord(1, 70, 5), new BlockCoord(2, 70, 5), new BlockCoord(4, 70, 5)))));

    @Test
    void of_Snapshot_EveryLotRectangleWithLabel() {
        List<TownMapView.MapRect> lots = VIEW.rects().stream()
                .filter(rect -> rect.kind() == TownMapView.Kind.LOT)
                .toList();
        assertEquals(PLAN.placements().size(), lots.size(), "Каждое место плана — прямоугольник участка на карте");
        TownMapView.MapRect hut = lots.stream()
                .filter(rect -> "Хижина".equals(rect.label()))
                .findFirst()
                .orElseThrow();
        assertEquals(
                List.of(75, true),
                List.of(hut.percent(), hut.damaged()),
                "Готовность участка — доля клеток схемы из снимка; повреждённый помечен для подсказки");
    }

    @Test
    void of_PathCells_MergedIntoRowRectangles() {
        List<TownMapView.MapRect> paths = VIEW.rects().stream()
                .filter(rect -> rect.kind() == TownMapView.Kind.PATH)
                .toList();
        assertEquals(
                List.of(
                        new TownMapView.MapRect(TownMapView.Kind.PATH, 1, 5, 2, 5, "", 0, false),
                        new TownMapView.MapRect(TownMapView.Kind.PATH, 4, 5, 4, 5, "", 0, false)),
                paths,
                "Соседние клетки тропинки в ряду — один прямоугольник, разрыв — новый: иначе карта рисует тысячи квадратов");
    }

    @Test
    void withMarkers_NewList_KeepsMapAndReplacesMarkers() {
        MapMarker spirit = new MapMarker(MapMarker.Kind.SPIRIT, "spirit-1", "", 10, 10);
        TownMapView marked = VIEW.withMarkers(List.of(spirit));
        assertEquals(VIEW.rects(), marked.rects(), "Метки не меняют участки и улицы карты");
        assertEquals(VIEW.lines(), marked.lines(), "Метки не меняют контур и ограду");
        assertEquals(List.of(spirit), marked.markers(), "Снимок карты несёт присланные метки");
        assertEquals(List.of(), VIEW.markers(), "Старый снимок без меток остаётся неизменным");
    }

    @Test
    void of_Snapshot_ContourAndStageFenceLines() {
        assertEquals(
                List.of(TownMapView.LineKind.CONTOUR, TownMapView.LineKind.STAGE_FENCE),
                VIEW.lines().stream().map(TownMapView.MapLine::kind).toList(),
                "Ограда на карте — две линии: контур участка и ограда этапа");
        assertFalse(VIEW.lines().get(0).points().isEmpty(), "Контур плана не пустой");
        assertFalse(VIEW.lines().get(0).gates().isEmpty(), "Калитки контура тоже на карте");
        TownMapView.Bounds bounds = VIEW.bounds();
        assertTrue(
                bounds.x2() - bounds.x1() > 60 && bounds.z2() - bounds.z1() > 60,
                "Границы карты охватывают участок ~100 блоков: " + bounds);
        Optional<TownMapView.MapRect> street = VIEW.rects().stream()
                .filter(rect -> rect.kind() == TownMapView.Kind.STREET_MAIN)
                .findFirst();
        assertTrue(street.isPresent(), "Главные улицы плана на карте");
    }
}
