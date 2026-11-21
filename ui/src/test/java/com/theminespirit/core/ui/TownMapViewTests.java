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
        assertEquals(
                PLAN.placements().size(),
                lots.size(),
                "Каждое место плана — прямоугольник участка на карте"
                        + " Иначе тест of_Snapshot_EveryLotRectangleWithLabel перестанет ловить эту поломку. При смене of_Snapshot_EveryLotRectangleWithLabel проверь это утверждение.");
        TownMapView.MapRect hut = lots.stream()
                .filter(rect -> "Хижина".equals(rect.label()))
                .findFirst()
                .orElseThrow();
        assertEquals(
                List.of(75, true),
                List.of(hut.percent(), hut.damaged()),
                "Готовность участка — доля клеток схемы из снимка; повреждённый помечен для подсказки"
                        + " Иначе тест of_Snapshot_EveryLotRectangleWithLabel перестанет ловить эту поломку. При смене of_Snapshot_EveryLotRectangleWithLabel проверь это утверждение.");
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
                "Соседние клетки тропинки в ряду — один прямоугольник, разрыв — новый: иначе карта рисует тысячи квадратов"
                        + " При смене of_PathCells_MergedIntoRowRectangles проверь это утверждение.");
    }

    @Test
    void withMarkers_NewList_KeepsMapAndReplacesMarkers() {
        MapMarker spirit = new MapMarker(MapMarker.Kind.SPIRIT, "spirit-1", "", 10, 10);
        TownMapView marked = VIEW.withMarkers(List.of(spirit));
        assertEquals(
                VIEW.rects(),
                marked.rects(),
                "Метки не меняют участки и улицы карты"
                        + " Иначе тест withMarkers_NewList_KeepsMapAndReplacesMarkers перестанет ловить эту поломку. При смене withMarkers_NewList_KeepsMapAndReplacesMarkers проверь это утверждение.");
        assertEquals(
                VIEW.lines(),
                marked.lines(),
                "Метки не меняют контур и ограду"
                        + " Иначе тест withMarkers_NewList_KeepsMapAndReplacesMarkers перестанет ловить эту поломку. При смене withMarkers_NewList_KeepsMapAndReplacesMarkers проверь это утверждение.");
        assertEquals(
                List.of(spirit),
                marked.markers(),
                "Снимок карты несёт присланные метки"
                        + " Иначе тест withMarkers_NewList_KeepsMapAndReplacesMarkers перестанет ловить эту поломку. При смене withMarkers_NewList_KeepsMapAndReplacesMarkers проверь это утверждение.");
        assertEquals(
                List.of(),
                VIEW.markers(),
                "Старый снимок без меток остаётся неизменным"
                        + " Иначе тест withMarkers_NewList_KeepsMapAndReplacesMarkers перестанет ловить эту поломку. При смене withMarkers_NewList_KeepsMapAndReplacesMarkers проверь это утверждение.");
    }

    @Test
    void of_Snapshot_ContourAndStageFenceLines() {
        assertEquals(
                List.of(TownMapView.LineKind.CONTOUR, TownMapView.LineKind.STAGE_FENCE),
                VIEW.lines().stream().map(TownMapView.MapLine::kind).toList(),
                "Ограда на карте — две линии: контур участка и ограда этапа"
                        + " Иначе тест of_Snapshot_ContourAndStageFenceLines перестанет ловить эту поломку. При смене of_Snapshot_ContourAndStageFenceLines проверь это утверждение.");
        assertFalse(
                VIEW.lines().get(0).points().isEmpty(),
                "Контур плана не пустой"
                        + " Иначе тест of_Snapshot_ContourAndStageFenceLines перестанет ловить эту поломку. При смене of_Snapshot_ContourAndStageFenceLines проверь это утверждение.");
        assertFalse(
                VIEW.lines().get(0).gates().isEmpty(),
                "Калитки контура тоже на карте"
                        + " Иначе тест of_Snapshot_ContourAndStageFenceLines перестанет ловить эту поломку. При смене of_Snapshot_ContourAndStageFenceLines проверь это утверждение.");
        TownMapView.Bounds bounds = VIEW.bounds();
        assertTrue(
                bounds.x2() - bounds.x1() > 60 && bounds.z2() - bounds.z1() > 60,
                "Границы карты охватывают участок ~100 блоков: " + bounds
                        + " Иначе тест of_Snapshot_ContourAndStageFenceLines перестанет ловить эту поломку. При смене of_Snapshot_ContourAndStageFenceLines проверь это утверждение.");
        Optional<TownMapView.MapRect> street = VIEW.rects().stream()
                .filter(rect -> rect.kind() == TownMapView.Kind.STREET_MAIN)
                .findFirst();
        assertTrue(
                street.isPresent(),
                "Главные улицы плана на карте"
                        + " Иначе тест of_Snapshot_ContourAndStageFenceLines перестанет ловить эту поломку. При смене of_Snapshot_ContourAndStageFenceLines проверь это утверждение.");
    }
}
