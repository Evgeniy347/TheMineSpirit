package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SubmitGateTests {
    private static final String PLAN = "demolish:272v5zce3xulc";

    @Test
    void allow_SameConditionsTwice_SecondIsDenied() {
        SubmitGate gate = new SubmitGate();

        assertTrue(
                gate.allow("demolish:overworld", PLAN),
                "Первый набор уборки выдаётся: без задания уборки нет"
                        + " Иначе тест allow_SameConditionsTwice_SecondIsDenied перестанет ловить эту поломку. При смене allow_SameConditionsTwice_SecondIsDenied проверь это утверждение.");
        assertFalse(
                gate.allow("demolish:overworld", PLAN),
                "Тот же набор координат не переиздаётся: иначе DISMANTLE_SHELTER создаётся каждую минуту на той же "
                        + "недостижимой цели"
                        + " При смене allow_SameConditionsTwice_SecondIsDenied проверь это утверждение.");
    }

    @Test
    void allow_ChangedConditions_IsAllowed() {
        SubmitGate gate = new SubmitGate();
        gate.allow("demolish:overworld", PLAN);

        assertTrue(
                gate.allow("demolish:overworld", "demolish:other"),
                "Смена набора — смена условий: новое задание уборки выдать можно"
                        + " Иначе тест allow_ChangedConditions_IsAllowed перестанет ловить эту поломку. При смене allow_ChangedConditions_IsAllowed проверь это утверждение.");
    }

    @Test
    void allow_AfterForget_IsAllowed() {
        SubmitGate gate = new SubmitGate();
        gate.allow("demolish:overworld", PLAN);

        gate.forget("demolish:overworld");

        assertTrue(
                gate.allow("demolish:overworld", PLAN),
                "После явного сброса области набор проверяется заново: мир мог измениться, пока задача ждала"
                        + " Иначе тест allow_AfterForget_IsAllowed перестанет ловить эту поломку. При смене allow_AfterForget_IsAllowed проверь это утверждение.");
    }
}
