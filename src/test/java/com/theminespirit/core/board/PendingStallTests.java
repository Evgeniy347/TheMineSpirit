package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PendingStallTests {
    @Test
    void observe_SameReasonLongerThanLimit_WarnsOnce() {
        PendingStall stall = new PendingStall();

        assertEquals(
                Optional.empty(),
                stall.observe("chop:2", "spirit-1 — занят: goto", 100),
                "Свежее ожидание не будит наблюдателя: сигнал нужен только застою");

        Optional<String> warning = stall.observe("chop:2", "spirit-1 — занят: goto", 100 + PendingStall.STALL_TICKS);

        assertTrue(
                warning.orElseThrow().contains("chop:2"),
                "Предупреждение обязано называть задание: иначе по строке лога не найти висящую задачу");
        assertTrue(
                warning.orElseThrow().contains("spirit-1 — занят: goto"),
                "Предупреждение обязано повторять waitReason: без неё непонятно, чего ждёт доска");
        assertTrue(
                warning.orElseThrow().contains(String.valueOf(PendingStall.STALL_TICKS)),
                "Предупреждение обязано называть предел ожидания: по нему настраивают порог");

        assertEquals(
                Optional.empty(),
                stall.observe("chop:2", "spirit-1 — занят: goto", 200 + PendingStall.STALL_TICKS),
                "Пока причина та же, второй раз не предупреждают: иначе застой заливает лог каждой пересборкой");
    }

    @Test
    void observe_ReasonChanged_StartsNewCountdown() {
        PendingStall stall = new PendingStall();
        stall.observe("chop:2", "держится духом", 0);

        assertEquals(
                Optional.empty(),
                stall.observe("chop:2", "spirit-1 — занят: goto", PendingStall.STALL_TICKS),
                "Смена причины — событие, а не застой: отсчёт начинается заново с новым waitReason");

        assertTrue(
                stall.observe("chop:2", "spirit-1 — занят: goto", 2 * PendingStall.STALL_TICKS)
                        .isPresent(),
                "Новая причина, продержавшаяся дольше предела, снова обязана дать предупреждение");
    }

    @Test
    void retain_RemovedTicket_ForgetsHistory() {
        PendingStall stall = new PendingStall();
        stall.observe("chop:2", "держится духом", 0);
        stall.retain(Set.of("other"));

        assertEquals(
                Optional.empty(),
                stall.observe("chop:2", "держится духом", PendingStall.STALL_TICKS),
                "Задание, ушедшее из PENDING, начинает отсчёт заново при возврате: старая история ему не приговор");
    }
}
