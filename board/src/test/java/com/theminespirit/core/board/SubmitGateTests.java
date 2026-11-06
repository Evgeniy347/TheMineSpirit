package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SubmitGateTests {
    private static final String PLAN = "demolish:272v5zce3xulc";

    @Test
    void allow_SameConditionsTwice_SecondIsDenied() {
        SubmitGate gate = new SubmitGate();

        assertTrue(gate.allow("demolish:overworld", PLAN), "Первый набор уборки выдаётся: без задания уборки нет");
        assertFalse(
                gate.allow("demolish:overworld", PLAN),
                "Тот же набор координат не переиздаётся: иначе DISMANTLE_SHELTER создаётся каждую минуту на той же "
                        + "недостижимой цели");
    }

    @Test
    void allow_ChangedConditions_IsAllowed() {
        SubmitGate gate = new SubmitGate();
        gate.allow("demolish:overworld", PLAN);

        assertTrue(
                gate.allow("demolish:overworld", "demolish:other"),
                "Смена набора — смена условий: новое задание уборки выдать можно");
    }

    @Test
    void allow_AfterForget_IsAllowed() {
        SubmitGate gate = new SubmitGate();
        gate.allow("demolish:overworld", PLAN);

        gate.forget("demolish:overworld");

        assertTrue(
                gate.allow("demolish:overworld", PLAN),
                "После явного сброса области набор проверяется заново: мир мог измениться, пока задача ждала");
    }
}
