package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class HelpCallGateTests {
    private static final int INTERVAL = 200;

    @Test
    void ready_FirstCall_IsDue() {
        assertTrue(
                new HelpCallGate().ready(0, INTERVAL),
                "Первый зов не ждёт: до него дух не звал и игрок ещё ничего не слышал");
    }

    @Test
    void ready_BeforeIntervalNotDue_AtIntervalDue() {
        HelpCallGate gate = new HelpCallGate();
        gate.called(100);

        assertFalse(
                gate.ready(100 + INTERVAL - 1, INTERVAL),
                "Зов не повторяется раньше T тиков: иначе чат забивается одним и тем же сообщением");
        assertTrue(
                gate.ready(100 + INTERVAL, INTERVAL),
                "На исходе T тиков зов снова разрешён: предел частоты, а не запрет навсегда");
    }

    @Test
    void ready_IntervalBelowOneTick_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new HelpCallGate().ready(0, 0),
                "Предел частоты меньше тика бессмыслен: зов шёл бы каждый тик");
    }

    @Property(tries = 200)
    void called_AnyTickSequence_NeverCloserThanInterval(
            @ForAll @IntRange(min = 1, max = 6000) int interval, @ForAll @IntRange(min = 0, max = 1000) int start) {
        HelpCallGate gate = new HelpCallGate();
        long last = -1;
        for (long now = start; now < start + 5000; now++) {
            if (!gate.ready(now, interval)) {
                continue;
            }
            if (last >= 0) {
                assertTrue(
                        now - last >= interval,
                        "Соседние зовы " + last + " и " + now + " ближе предела " + interval + ": чат игрока "
                                + "превращается в спам, а сигнал помощи обесценивается");
            }
            gate.called(now);
            last = now;
        }
        assertEquals(true, last >= 0, "За 5000 тиков исправный зов обязан сработать хотя бы раз");
    }
}
