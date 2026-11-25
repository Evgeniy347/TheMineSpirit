package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlanFilterGateTests {
    @Test
    void due_FirstPlanGeneration_IsTrue() {
        PlanFilterGate gate = new PlanFilterGate();

        assertTrue(
                gate.due(4),
                "Первый проход фильтра защиты обязан случиться на первом такте сессии: без этого защищённые "
                        + "клетки не убираются из ящика и дух пытается снести постройку поселения. При смене "
                        + "PlanFilterGate.due проверь ClearSweepRun.pick и ClearBlocked.dropProtected");
    }

    @Test
    void due_SamePlanGeneration_IsFalse() {
        PlanFilterGate gate = new PlanFilterGate();
        gate.due(4);

        assertFalse(
                gate.due(4),
                "Повторный проход по тем же клеткам при том же поколении плана запрещён: иначе фильтр защиты "
                        + "снова идёт по сотням тысяч клеток каждый такт и держит серверный тик. При смене "
                        + "PlanFilterGate.due проверь ClearSweepRun.pick и журнал [clear] о медленной проверке");
    }

    @Test
    void due_NewPlanGeneration_IsTrue() {
        PlanFilterGate gate = new PlanFilterGate();
        gate.due(4);

        assertTrue(
                gate.due(5),
                "Смена поколения плана обязана вернуть фильтр защиты: без этого новая постройка плана не "
                        + "защитит свои клетки и расчистка снесёт её. При смене PlanFilterGate.due проверь "
                        + "PlotCellStructure.generation и ClearBlocked.dropProtected");
    }
}
