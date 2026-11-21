package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.engine.RunStatus;
import org.junit.jupiter.api.Test;

class ReturnGateTests {
    @Test
    void allowed_WhileTaskRuns_IsFalse() {
        assertFalse(
                ReturnGate.allowed(RunStatus.RUNNING),
                "При работающем задании тактический возврат к якорю запрещён: иначе скрипт задания и возврат "
                        + "каждый тик строят путь к разным целям, дух дёргается на месте (окно 21, TacticsReturn.walk "
                        + "636 построек рядом с SchematicWorkPhase 201). При смене гейта проверь живое задание "
                        + "и якорь поселения: дух идёт к цели задания, а не к якорю");
    }

    @Test
    void allowed_AfterTaskFinished_IsTrue() {
        assertTrue(
                ReturnGate.allowed(RunStatus.SUCCEEDED),
                "После завершения задания тактический возврат разрешён: дух возвращается к якорю поселения своим "
                        + "ходом. Иначе после любой успешной работы дух застрял бы на месте задания и никогда не "
                        + "вернулся домой. При смене ReturnGate.allowed проверь завершение задания доской и "
                        + "последующий возврат к якорю");
    }

    @Test
    void allowed_WithoutTask_IsTrue() {
        assertTrue(
                ReturnGate.allowed(null),
                "Без задания тактический возврат разрешён: свободный дух должен возвращаться к якорю, иначе "
                        + "после работы он навсегда останется на месте последнего задания, а поселение теряет "
                        + "помощника у якоря. При смене гейта проверь свободного духа без задания: он идёт к якорю");
    }

    @Test
    void allowed_AfterFailedTask_IsTrue() {
        assertTrue(
                ReturnGate.allowed(RunStatus.FAILED),
                "После провала задания возврат разрешён: дух не должен торчать у места провала, якорь — "
                        + "безопасная точка поселения. Иначе застрявший дух копил бы повторные провалы у одной ямы. "
                        + "При смене гейта проверь провалившееся задание доски и уход духа к якорю");
    }
}
