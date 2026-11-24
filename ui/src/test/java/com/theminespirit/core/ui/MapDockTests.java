package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class MapDockTests {
    private static TownMapView.MapRect lot(int x, int z, int percent, boolean damaged) {
        return new TownMapView.MapRect(TownMapView.Kind.LOT, x, z, x + 5, z + 5, "lot", percent, damaged);
    }

    @Test
    void stats_AllLotKinds_CountsLotStates() {
        List<TownMapView.MapRect> rects = List.of(
                lot(0, 0, 100, false),
                lot(10, 0, 40, false),
                lot(20, 0, 0, false),
                lot(30, 0, 70, true),
                new TownMapView.MapRect(TownMapView.Kind.STREET_MAIN, 0, 0, 40, 2, "", 0, false));
        MapDock.Stats stats = MapDock.stats(rects);
        assertEquals(
                1,
                stats.done(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене stats_AllLotKinds_CountsLotStates проверь это утверждение.");
        assertEquals(
                1,
                stats.started(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене stats_AllLotKinds_CountsLotStates проверь это утверждение.");
        assertEquals(
                1,
                stats.empty(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене stats_AllLotKinds_CountsLotStates проверь это утверждение.");
        assertEquals(
                1,
                stats.damaged(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене stats_AllLotKinds_CountsLotStates проверь это утверждение.");
        assertEquals(
                4,
                stats.total(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене stats_AllLotKinds_CountsLotStates проверь это утверждение.");
    }

    @Test
    void nearby_LotsAround_SortsByDistanceToCenter() {
        List<TownMapView.MapRect> rects =
                List.of(lot(100, 100, 50, false), lot(2, 2, 50, false), lot(50, 50, 50, false));
        List<TownMapView.MapRect> near = MapDock.nearby(rects, 0, 0, 2);
        assertEquals(
                2,
                near.size(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене nearby_LotsAround_SortsByDistanceToCenter проверь это утверждение.");
        assertEquals(
                2,
                near.get(0).x1(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене nearby_LotsAround_SortsByDistanceToCenter проверь это утверждение.");
        assertEquals(
                50,
                near.get(1).x1(),
                "Механизм обязан вести себя так, как описано в проверке: иначе поломка пройдёт незамеченной и сломается экран поселения при следующей правке. При смене nearby_LotsAround_SortsByDistanceToCenter проверь это утверждение.");
    }
}
