package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TicketIsolationTests {
    @Test
    void post_TicketThrows_NextTicketStillPosted() {
        boolean broken = TicketIsolation.post("graph:broken", () -> {
            throw new IllegalArgumentException("Постройка по месту без схемы");
        });
        boolean healthy = TicketIsolation.post("graph:healthy", () -> true);

        assertFalse(broken, "упавшее задание не считается поставленным");
        assertTrue(healthy, "соседнее задание ставится: иначе один сбой оставляет всех духов без работы");
    }
}
