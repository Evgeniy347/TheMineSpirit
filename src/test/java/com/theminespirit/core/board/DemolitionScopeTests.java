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
                        + "(observe24: висящих 204, задач clear_floating нет)");
    }

    @Test
    void of_DemolishStrayTicket_OnlyStrays() {
        assertEquals(
                DemolitionScope.STRAYS,
                DemolitionScope.of("graph:event:demolish_stray:demolish_stray"),
                "Задание узла demolish_stray убирает лишние постройки, а не висящее");
    }

    @Test
    void of_TwoGraphNodes_DoNotShadowEachOther() {
        assertNotEquals(
                DemolitionScope.of("graph:event:clear_floating:clear_floating"),
                DemolitionScope.of("graph:event:demolish_stray:demolish_stray"),
                "Узлы уборки — разные очереди: busy/cooldown одного не должен отменять задачу другого");
    }

    @Test
    void of_OtherCause_Both() {
        assertEquals(
                DemolitionScope.BOTH,
                DemolitionScope.of("always"),
                "Неизвестная причина уборки не делится: исполнитель берёт и лишнее, и висящее");
    }
}
