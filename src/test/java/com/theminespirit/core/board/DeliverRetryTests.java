package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeliverRetryTests {
    @Test
    void canRetry_FirstTimeouts_TrueUntilBudget() {
        assertTrue(
                DeliverRetry.canRetry(0),
                "После первого таймаута доставщик сближается снова: адресат ушёл рубить лес, а не исчез (N12)");
        assertTrue(DeliverRetry.canRetry(DeliverRetry.MAX_RESTARTS - 1), "Последняя попытка ещё разрешена");
        assertFalse(
                DeliverRetry.canRetry(DeliverRetry.MAX_RESTARTS),
                "После исчерпания попыток передача проваливается с причиной, а не крутится вечно");
    }
}
