package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DemolitionRetryTests {
    private static final String SET = "demolish:3w5e10zw6ogd3";
    private static final long COOLDOWN = 600;

    @Test
    void waiting_BeforeCooldown_SuppressesTheSameSet() {
        DemolitionRetry retry = new DemolitionRetry();
        retry.submitted(SET, 1000, COOLDOWN);

        assertTrue(
                retry.waiting(SET, 1599),
                "Повтор того же набора уборки до конца паузы не выдаётся: иначе уборка создаётся каждый тик");
        assertFalse(
                retry.waiting(SET, 1600),
                "После паузы набор обязан снова стать кандидатом: иначе оставшиеся блоки висят навсегда");
    }

    @Test
    void signature_AfterFailedAttempt_ChangesSoTheGateAllowsRetry() {
        SubmitGate gate = new SubmitGate();
        DemolitionRetry retry = new DemolitionRetry();

        String first = retry.signature(SET);
        assertTrue(gate.allow("demolish:overworld", first), "Первая уборка выдаётся");
        assertFalse(
                gate.allow("demolish:overworld", first),
                "Внутри одной попытки подпись не меняется — повтор не выдаётся");
        retry.submitted(SET, 1000, COOLDOWN);
        String second = retry.signature(SET);
        assertFalse(first.equals(second), "Новая попытка обязана иметь новую подпись: " + second);
        assertTrue(
                gate.allow("demolish:overworld", second),
                "После провала и паузы тот же набор обязан получить новую попытку: живой разбор закрылся DONE "
                        + "без единого убранного блока и больше не пересоздавался");
    }

    @Test
    void until_AfterSubmittedAttempt_NamesRetryTick() {
        DemolitionRetry retry = new DemolitionRetry();
        retry.submitted(SET, 1000, COOLDOWN);

        assertEquals(1600, retry.until(SET), "Журнал обязан называть тик повтора, а не молчать");
        assertEquals(0, retry.until("demolish:other"), "Незнакомый набор ещё не ждёт повтора");
    }
}
