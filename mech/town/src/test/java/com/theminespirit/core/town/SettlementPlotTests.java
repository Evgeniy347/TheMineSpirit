package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettlementPlotTests {
    @Test
    void center_NoHomes_FailsWithReason() {
        IllegalArgumentException refusal = assertThrows(
                IllegalArgumentException.class,
                () -> SettlementPlot.center(List.of()),
                "Центр поселения без домов — ошибка, а не выдуманная точка: иначе вызывающий молча уходит в "
                        + "пустой угол мира" + " При смене center_NoHomes_FailsWithReason проверь это утверждение.");

        assertTrue(
                refusal.getMessage().contains("Нет ни одного дома"),
                "Центр поселения без домов не выдумывается: исключение называет причину, иначе вызывающий "
                        + "молча получит координату из воздуха; получено: " + refusal.getMessage()
                        + " При смене center_NoHomes_FailsWithReason проверь это утверждение.");
    }

    @Test
    void center_SpiritOrder_DoesNotMoveHome() {
        BlockCoord first = new BlockCoord(-171, 72, -497);
        BlockCoord second = new BlockCoord(-165, 72, -491);
        BlockCoord third = new BlockCoord(-168, 72, -494);

        assertEquals(
                SettlementPlot.center(List.of(first, second, third)),
                SettlementPlot.center(List.of(third, first, second)),
                "Точка поселения не зависит от порядка духов: иначе сцена и цель выхода меняются от "
                        + "порядка сущностей в мире"
                        + " При смене center_SpiritOrder_DoesNotMoveHome проверь это утверждение.");
    }

    @Test
    void requireCovered_CenterBeyondDrift_FailsWithBothAnchors() {
        SettlementPlot plot = new SettlementPlot(new BlockCoord(-171, 64, -489), 63, 64, 1, "oak");

        IllegalStateException refusal = assertThrows(
                IllegalStateException.class,
                () -> plot.requireCovered(new BlockCoord(-166, 70, -392)),
                "Устаревший участок обязан падать громко: иначе граф молча выходит каждый цикл и стройка стоит"
                        + " При смене requireCovered_CenterBeyondDrift_FailsWithBothAnchors проверь это утверждение.");
        assertTrue(
                refusal.getMessage().contains("x=-171") && refusal.getMessage().contains("z=-392"),
                "Ошибка называет оба якоря — участок и центр, иначе непонятно, что перемерять; получено: "
                        + refusal.getMessage()
                        + " При смене requireCovered_CenterBeyondDrift_FailsWithBothAnchors проверь это утверждение.");
    }

    @Test
    void requireCovered_CenterWithinDrift_Passes() {
        SettlementPlot plot = new SettlementPlot(new BlockCoord(-171, 64, -489), 63, 64, 1, "oak");
        BlockCoord center = new BlockCoord(-166, 70, -484);

        plot.requireCovered(center);
        assertTrue(
                plot.covers(center),
                "Центр в пределах допуска не должен валить обмер участка: иначе нормальный, только что перемерянный "
                        + "участок начнёт ронять граф при каждом цикле"
                        + " При смене requireCovered_CenterWithinDrift_Passes проверь это утверждение.");
    }
}
