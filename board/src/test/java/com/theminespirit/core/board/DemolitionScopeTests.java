package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class DemolitionScopeTests {
    @Test
    void of_ClearFloatingTicket_OnlyFloating() {
        assertEquals(
                DemolitionScope.FLOATING,
                DemolitionScope.of("graph:event:clear_floating:clear_floating"),
                "Задание узла clear_floating убирает только висящее: иначе его снова заслоняет demolish_stray "
                        + "(observe24: висящих 204, задач clear_floating нет)"
                        + " При смене of_ClearFloatingTicket_OnlyFloating проверь это утверждение.");
    }

    @Test
    void of_DemolishStrayTicket_OnlyStrays() {
        assertEquals(
                DemolitionScope.STRAYS,
                DemolitionScope.of("graph:event:demolish_stray:demolish_stray"),
                "Задание узла demolish_stray убирает лишние постройки, а не висящее"
                        + " Иначе тест of_DemolishStrayTicket_OnlyStrays перестанет ловить эту поломку. При смене of_DemolishStrayTicket_OnlyStrays проверь это утверждение.");
    }

    @Test
    void of_TwoGraphNodes_DoNotShadowEachOther() {
        assertNotEquals(
                DemolitionScope.of("graph:event:clear_floating:clear_floating"),
                DemolitionScope.of("graph:event:demolish_stray:demolish_stray"),
                "Узлы уборки — разные очереди: busy/cooldown одного не должен отменять задачу другого"
                        + " Иначе тест of_TwoGraphNodes_DoNotShadowEachOther перестанет ловить эту поломку. При смене of_TwoGraphNodes_DoNotShadowEachOther проверь это утверждение.");
    }

    @Test
    void of_OtherCause_Both() {
        assertEquals(
                DemolitionScope.BOTH,
                DemolitionScope.of("always"),
                "Неизвестная причина уборки не делится: исполнитель берёт и лишнее, и висящее"
                        + " Иначе тест of_OtherCause_Both перестанет ловить эту поломку. При смене of_OtherCause_Both проверь это утверждение.");
    }
}
