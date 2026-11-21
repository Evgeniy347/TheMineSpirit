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

    @Test
    void allowed_WithActiveScript_IsFalse() {
        assertFalse(
                ReturnGate.allowed(RunStatus.SUCCEEDED, true),
                "При активном скрипте движения тактический возврат запрещён даже без RUNNING-задания: иначе "
                        + "acquire и возврат каждый тик строят путь к разным целям — сундук -177,76,-517 против "
                        + "якоря -156,70,-489 (окно 37, spirit-1: пути чередуются, путь сбрасывается из "
                        + "GotoScript.tick, дух вертится 298°/103 тика у сундука). При смене гейта проверь живой "
                        + "acquire у сундука: дух доводит скрипт до конца и возвращается после него");
    }

    @Test
    void allowed_FinishedTaskWithoutScript_IsTrue() {
        assertTrue(
                ReturnGate.allowed(RunStatus.SUCCEEDED, false),
                "После завершённой задачи без активного скрипта возврат разрешён: иначе дух останется у "
                        + "последней работы и якорь поселения опустеет. При смене гейта проверь живой возврат "
                        + "после успешной задачи: дух идёт к якорю");
    }

    @Test
    void allowed_WhileTaskRunsWithScript_IsFalse() {
        assertFalse(
                ReturnGate.allowed(RunStatus.RUNNING, true),
                "RUNNING-задание с активным скриптом запрещает возврат по обоим признакам: иначе гейт можно "
                        + "обойти, и путь к якорю снова начнёт перебивать путь задания. При смене гейта проверь "
                        + "живое задание доски: дух идёт к цели задания, а не к якорю");
    }
}
