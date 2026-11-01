package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class TickWatchTests {
    @Test
    void describe_UnderLimit_IsSilent() {
        assertNull(
                TickWatch.describe("RouteDetour", 499_999_999L),
                "Операция короче 500 мс не попадает в лог: иначе сторож шумит на обычных тиках");
    }

    @Test
    void describe_OverLimit_NamesOperationAndMilliseconds() {
        String report = TickWatch.describe("RouteDetour", 2_750_000_000L);

        assertEquals(
                "[tick] операция RouteDetour держала серверный поток 2750 мс",
                report,
                "Сообщение сторожа называет класс операции и время: по нему ищут виновника в живом мире");
    }
}
