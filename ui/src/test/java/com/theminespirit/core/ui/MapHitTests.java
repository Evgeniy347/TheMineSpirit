package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MapHitTests {
    private static final TownMapView.MapRect BIG =
            new TownMapView.MapRect(TownMapView.Kind.LOT, 0, 0, 19, 19, "Площадь", 0, false);
    private static final TownMapView.MapRect SMALL =
            new TownMapView.MapRect(TownMapView.Kind.LOT, 5, 5, 8, 8, "Колодец", 40, false);
    private static final TownMapView.MapRect STREET =
            new TownMapView.MapRect(TownMapView.Kind.STREET_MAIN, 20, 0, 26, 19, "", 0, false);
    private static final TownMapView VIEW = new TownMapView(List.of(STREET, BIG, SMALL), List.of());
    private static final MapViewport VIEWPORT = MapViewport.fit(VIEW.bounds(), 0, 0, 270, 200);

    @Test
    void lotAt_CursorOverNestedLots_SmallestWins() {
        assertEquals(
                Optional.of(SMALL),
                MapHit.lotAt(VIEW, VIEWPORT, VIEWPORT.screenX(6.5), VIEWPORT.screenY(6.5)),
                "Под курсором два участка — подсказка о меньшем: он нарисован поверх"
                        + " Иначе тест lotAt_CursorOverNestedLots_SmallestWins перестанет ловить эту поломку. При смене lotAt_CursorOverNestedLots_SmallestWins проверь это утверждение.");
        assertEquals(
                Optional.of(BIG),
                MapHit.lotAt(VIEW, VIEWPORT, VIEWPORT.screenX(1.5), VIEWPORT.screenY(18.5)),
                "Курсор на участке — его подсказка"
                        + " Иначе тест lotAt_CursorOverNestedLots_SmallestWins перестанет ловить эту поломку. При смене lotAt_CursorOverNestedLots_SmallestWins проверь это утверждение.");
    }

    @Test
    void lotAt_StreetOrOutside_NoHint() {
        assertEquals(
                Optional.empty(),
                MapHit.lotAt(VIEW, VIEWPORT, VIEWPORT.screenX(22), VIEWPORT.screenY(3)),
                "Улица — не участок: подсказки нет"
                        + " Иначе тест lotAt_StreetOrOutside_NoHint перестанет ловить эту поломку. При смене lotAt_StreetOrOutside_NoHint проверь это утверждение.");
        assertEquals(
                Optional.empty(),
                MapHit.lotAt(VIEW, VIEWPORT, -5, 10),
                "Вне окна карты подсказки нет"
                        + " Иначе тест lotAt_StreetOrOutside_NoHint перестанет ловить эту поломку. При смене lotAt_StreetOrOutside_NoHint проверь это утверждение.");
    }

    @Test
    void boxes_LotPartlyOffWindow_Clipped() {
        MapViewport zoomed = VIEWPORT.pan(-60, -60);
        MapBoxes.Box box = MapBoxes.of(BIG, zoomed).orElseThrow();
        assertEquals(
                List.of(0, 0),
                List.of(box.x1(), box.y1()),
                "Прямоугольник за краем окна обрезан по окну: рисовать за рамкой карты нельзя"
                        + " Иначе тест boxes_LotPartlyOffWindow_Clipped перестанет ловить эту поломку. При смене boxes_LotPartlyOffWindow_Clipped проверь это утверждение.");
    }
}
