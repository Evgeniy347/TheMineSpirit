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
                "Под курсором два участка — подсказка о меньшем: он нарисован поверх");
        assertEquals(
                Optional.of(BIG),
                MapHit.lotAt(VIEW, VIEWPORT, VIEWPORT.screenX(1.5), VIEWPORT.screenY(18.5)),
                "Курсор на участке — его подсказка");
    }

    @Test
    void lotAt_StreetOrOutside_NoHint() {
        assertEquals(
                Optional.empty(),
                MapHit.lotAt(VIEW, VIEWPORT, VIEWPORT.screenX(22), VIEWPORT.screenY(3)),
                "Улица — не участок: подсказки нет");
        assertEquals(Optional.empty(), MapHit.lotAt(VIEW, VIEWPORT, -5, 10), "Вне окна карты подсказки нет");
    }

    @Test
    void boxes_LotPartlyOffWindow_Clipped() {
        MapViewport zoomed = VIEWPORT.pan(-60, -60);
        MapBoxes.Box box = MapBoxes.of(BIG, zoomed).orElseThrow();
        assertEquals(
                List.of(0, 0),
                List.of(box.x1(), box.y1()),
                "Прямоугольник за краем окна обрезан по окну: рисовать за рамкой карты нельзя");
    }
}
