package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MineHitTests {
    private static final MineSnapshot.Rect BIG = new MineSnapshot.Rect(0, 0, 9, 4, MineSnapshot.DESCENT, 20);
    private static final MineSnapshot.Rect SMALL = new MineSnapshot.Rect(5, 0, 8, 3, MineSnapshot.TUNNEL, 80);
    private static final MineSnapshot VIEW = new MineSnapshot(
            new TownMapView.Bounds(0, 0, 19, 9), 10, 30, 20, List.of(), List.of(BIG, SMALL), List.of());
    private static final MapViewport VIEWPORT = MapViewport.fit(VIEW.bounds(), 0, 0, 270, 200);

    @Test
    void rectAt_NestedPlans_SmallestWins() {
        assertEquals(
                Optional.of(SMALL),
                MineHit.rectAt(VIEW, VIEWPORT, VIEWPORT.screenX(6.5), VIEWPORT.screenY(1.5)),
                "Под курсором спуск и ход — подсказка о меньшем: он нарисован поверх"
                        + " Иначе тест rectAt_NestedPlans_SmallestWins перестанет ловить эту поломку. При смене rectAt_NestedPlans_SmallestWins проверь это утверждение.");
        assertEquals(
                Optional.of(BIG),
                MineHit.rectAt(VIEW, VIEWPORT, VIEWPORT.screenX(1.5), VIEWPORT.screenY(3.5)),
                "Курсор на спуске — его подсказка"
                        + " Иначе тест rectAt_NestedPlans_SmallestWins перестанет ловить эту поломку. При смене rectAt_NestedPlans_SmallestWins проверь это утверждение.");
    }

    @Test
    void rectAt_OutsideMapOrPlans_NoHint() {
        assertEquals(
                Optional.empty(),
                MineHit.rectAt(VIEW, VIEWPORT, VIEWPORT.screenX(18.5), VIEWPORT.screenY(8.5)),
                "Вне прямоугольников шахты подсказки нет"
                        + " Иначе тест rectAt_OutsideMapOrPlans_NoHint перестанет ловить эту поломку. При смене rectAt_OutsideMapOrPlans_NoHint проверь это утверждение.");
        assertEquals(
                Optional.empty(),
                MineHit.rectAt(VIEW, VIEWPORT, -5, 10),
                "Вне окна карты подсказки нет"
                        + " Иначе тест rectAt_OutsideMapOrPlans_NoHint перестанет ловить эту поломку. При смене rectAt_OutsideMapOrPlans_NoHint проверь это утверждение.");
    }
}
