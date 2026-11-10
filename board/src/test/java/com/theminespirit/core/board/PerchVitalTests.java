package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.VitalNeeds;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PerchVitalTests {
    private static final VitalNeeds.Need PERCH = VitalNeeds.byId("perch").orElseThrow();
    private static final VitalNeeds.Need HUNGER = VitalNeeds.byId("hunger").orElseThrow();

    private static BoardTicket ticket(String id, BoardCase boardCase) {
        return new BoardTicket(
                id, boardCase, Optional.empty(), 50, 0, 1, BoardTicket.ALWAYS_CAUSE, List.of(), Map.of());
    }

    @Test
    void perchNeed_Table_GatedWithRescuePath() {
        assertTrue(PERCH.gate(), "Дух на своих подмостях не берёт обычную работу: сначала спуск");
        assertTrue(PERCH.allows("graph:rescue_spirit"), "Путь нужды — спасение со спуском по своему столбу");
        assertTrue(
                PERCH.allows("repair:clear_grounds"),
                "Подмости строили ради расчистки: продолжить её с вершины — тоже путь нужды");
    }

    @Test
    void perched_AfterInterruption_OrdinaryWorkRefusedUntilDescended() {
        Map<String, String> live = Map.of(PERCH.id(), PERCH.blocked());

        assertTrue(
                VitalGate.refusal(
                                ticket("graph:event:storage_mixed:sort_chest:1", BoardCase.ACTIVITY_UNLOAD),
                                live,
                                VitalNeeds.needs())
                        .isPresent(),
                "spirit-4 10.11: перебитый на вершине столба y=98, дух получал сундуки и ягоды внизу, все падали "
                        + "«маршрут не найден», и через 10 минут граф перестал давать ему работу — стоял часами");
        assertFalse(
                VitalGate.refusal(ticket("graph:rescue_spirit", BoardCase.RESCUE_SPIRIT), live, VitalNeeds.needs())
                        .isPresent(),
                "Спасение со спуском выдаётся");
        assertFalse(
                VitalGate.refusal(ticket("repair:clear_grounds", BoardCase.REPAIR_BUILDING), live, VitalNeeds.needs())
                        .isPresent(),
                "Свою расчистку с подмостей можно продолжить");
    }

    @Test
    void hungry_OnScaffold_EatsInPlaceButDoesNotWalkAway() {
        Map<String, String> live = Map.of(PERCH.id(), PERCH.blocked(), HUNGER.id(), HUNGER.blocked());

        assertFalse(
                VitalGate.refusal(ticket("graph:event:hungry:eat", BoardCase.EAT_TO_FULL), live, VitalNeeds.needs())
                        .isPresent(),
                "Голод на подмостях: поесть из инвентаря можно");
        assertTrue(
                VitalGate.refusal(
                                ticket("graph:event:storage_mixed:sort_chest:1", BoardCase.ACTIVITY_UNLOAD),
                                live,
                                VitalNeeds.needs())
                        .isPresent(),
                "Работа внизу всё равно ждёт спуска");
    }

    @Test
    void descended_NeedGone_WorkReturns() {
        assertFalse(
                VitalGate.refusal(
                                ticket("graph:event:storage_mixed:sort_chest:1", BoardCase.ACTIVITY_UNLOAD),
                                Map.of(),
                                VitalNeeds.needs())
                        .isPresent(),
                "После спуска нужда гаснет: обратная связь возвращает духа к работе");
    }
}
