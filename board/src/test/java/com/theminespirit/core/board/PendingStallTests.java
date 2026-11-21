package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PendingStallTests {
    @Test
    void summary_FreshPending_IsSilent() {
        PendingStall stall = new PendingStall(100);

        stall.observe("chop:2", "spirit-1 — занят: goto", 100);

        assertEquals(
                Optional.empty(),
                stall.summary(149),
                "Свежее ожидание не будит наблюдателя: сигнал нужен только застою"
                        + " Иначе тест summary_FreshPending_IsSilent перестанет ловить эту поломку. При смене summary_FreshPending_IsSilent проверь это утверждение.");
    }

    @Test
    void summary_OneTicketStalledLong_NamesTicketReasonAndCount() {
        PendingStall stall = new PendingStall(100);
        stall.observe("chop:2", "spirit-1 — занят: goto", 100);

        String text = stall.summary(100 + PendingStall.STALL_TICKS + 100).orElseThrow();

        assertTrue(
                text.contains("chop:2"),
                "Сводка обязана называть задания: иначе по строке лога не найти " + "висящую задачу"
                        + " При смене summary_OneTicketStalledLong_NamesTicketReasonAndCount проверь это утверждение.");
        assertTrue(
                text.contains("spirit-1 — занят: goto"),
                "Сводка обязана повторять waitReason: без неё непонятно, " + "чего ждёт доска"
                        + " Иначе тест summary_OneTicketStalledLong_NamesTicketReasonAndCount перестанет ловить эту поломку. При смене summary_OneTicketStalledLong_NamesTicketReasonAndCount проверь это утверждение.");
        assertTrue(
                text.contains("1 заданий висят"),
                "Сводка обязана называть число висящих: по нему видно масштаб"
                        + " Иначе тест summary_OneTicketStalledLong_NamesTicketReasonAndCount перестанет ловить эту поломку. При смене summary_OneTicketStalledLong_NamesTicketReasonAndCount проверь это утверждение.");
        assertTrue(
                text.contains(String.valueOf(PendingStall.STALL_TICKS)),
                "Сводка обязана называть предел ожидания: " + "по нему настраивают порог"
                        + " Иначе тест summary_OneTicketStalledLong_NamesTicketReasonAndCount перестанет ловить эту поломку. При смене summary_OneTicketStalledLong_NamesTicketReasonAndCount проверь это утверждение.");
    }

    @Test
    void summary_ManyTicketsStalled_GivesOneLine() {
        PendingStall stall = new PendingStall(100);
        for (int i = 0; i < 20; i++) {
            stall.observe("chop:" + i, "нет прогресса 3600 тиков", 100);
        }

        String text = stall.summary(100 + PendingStall.STALL_TICKS + 101).orElseThrow();

        assertTrue(
                text.contains("20 заданий"),
                "Двадцать висящих заданий идут одной сводкой: иначе лог заливает "
                        + "по строке на тикет каждые несколько секунд"
                        + " При смене summary_ManyTicketsStalled_GivesOneLine проверь это утверждение.");
        assertTrue(
                text.contains("20× нет прогресса 3600 тиков"),
                "Сводка группирует тикеты по причине: без группы " + "не видно, что причина общая"
                        + " Иначе тест summary_ManyTicketsStalled_GivesOneLine перестанет ловить эту поломку. При смене summary_ManyTicketsStalled_GivesOneLine проверь это утверждение.");
        assertTrue(
                text.contains("и ещё 15"),
                "Сводка называет примеры и остаток: полный список не нужен, но " + "остаток терять нельзя"
                        + " Иначе тест summary_ManyTicketsStalled_GivesOneLine перестанет ловить эту поломку. При смене summary_ManyTicketsStalled_GivesOneLine проверь это утверждение.");
    }

    @Test
    void summary_BeforeWindow_IsSilentUntilDue() {
        PendingStall stall = new PendingStall(5000);
        stall.observe("chop:2", "нет прогресса", 0);

        assertEquals(
                Optional.empty(),
                stall.summary(PendingStall.STALL_TICKS + 500),
                "До срока сводки повторы не печатаются: одно и то же состояние не новость"
                        + " Иначе тест summary_BeforeWindow_IsSilentUntilDue перестанет ловить эту поломку. При смене summary_BeforeWindow_IsSilentUntilDue проверь это утверждение.");
    }

    @Test
    void summary_AfterWindow_RepeatsOncePerWindow() {
        PendingStall stall = new PendingStall(1000);
        stall.observe("chop:2", "нет прогресса", 0);
        assertTrue(
                stall.summary(2000).isPresent(),
                "Первая сводка приходит в срок окна"
                        + " Иначе тест summary_AfterWindow_RepeatsOncePerWindow перестанет ловить эту поломку. При смене summary_AfterWindow_RepeatsOncePerWindow проверь это утверждение.");

        assertEquals(
                Optional.empty(),
                stall.summary(2500),
                "Между окнами сводка молчит: повтор не печатается на каждой пересборке доски"
                        + " Иначе тест summary_AfterWindow_RepeatsOncePerWindow перестанет ловить эту поломку. При смене summary_AfterWindow_RepeatsOncePerWindow проверь это утверждение.");
    }

    @Test
    void summary_ReasonChanged_ShowsNewReasonAndRestartsCountdown() {
        PendingStall stall = new PendingStall(100);
        stall.observe("chop:2", "держится духом", 0);
        stall.observe("chop:2", "spirit-1 — занят: goto", PendingStall.STALL_TICKS);

        String text = stall.summary(PendingStall.STALL_TICKS * 2 + 100).orElseThrow();

        assertTrue(
                text.contains("spirit-1 — занят: goto"),
                "Смена причины — событие: сводка обязана показать новую "
                        + "причину, иначе отладка пойдёт по устаревшей"
                        + " При смене summary_ReasonChanged_ShowsNewReasonAndRestartsCountdown проверь это утверждение.");
        assertTrue(
                text.contains("с тика " + PendingStall.STALL_TICKS),
                "Новая причина отсчитывает застой заново: " + "иначе старый срок приписывается новому состоянию"
                        + " При смене summary_ReasonChanged_ShowsNewReasonAndRestartsCountdown проверь это утверждение.");
    }

    @Test
    void retain_RemovedTicket_ForgetsHistory() {
        PendingStall stall = new PendingStall(10);
        stall.observe("chop:2", "держится духом", 0);
        stall.retain(Set.of("other"));

        assertEquals(
                Optional.empty(),
                stall.summary(PendingStall.STALL_TICKS + 100),
                "Задание, ушедшее из PENDING, забыто: иначе сводка вспоминает давно закрытую задачу"
                        + " При смене retain_RemovedTicket_ForgetsHistory проверь это утверждение.");
    }
}
