package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClaimGraceTests {
    @Test
    void expired_AssignedPartNotStarted_ReturnsToQueueAfterShortTime() {
        assertFalse(
                ClaimGrace.expired(1000, 1000 + ClaimGrace.TICKS - 1),
                "Назначенная часть, которую дух ещё может начать, держится за ним весь короткий срок: до "
                        + ClaimGrace.TICKS + " тиков задание не отбирают");
        assertTrue(
                ClaimGrace.expired(1000, 1000 + ClaimGrace.TICKS),
                "За " + ClaimGrace.TICKS + " тиков дух обязан начать назначенную часть: иначе она возвращается "
                        + "в очередь другому — иначе часть ограды простаивает до конца сцены");
    }
}
