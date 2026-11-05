package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PlotEditWhoTests {
    @Test
    void culprit_TicketAndScript_NamesBoth() {
        String text = PlotEditWho.culprit(List.of("graph:building:hut"), "build_schematic");

        assertTrue(text.contains("задание graph:building:hut"), "Задание-виновник названо: " + text);
        assertTrue(text.contains("действие build_schematic"), "Действие духа названо: " + text);
    }

    @Test
    void culprit_NoTicket_NamesScriptOnly() {
        String text = PlotEditWho.culprit(List.of(), "clear_area");

        assertEquals(", действие clear_area", text, "Без задания видно хотя бы действие: иначе правку мира не найти");
    }

    @Test
    void culprit_NothingKnown_IsEmpty() {
        assertEquals("", PlotEditWho.culprit(List.of(), null), "Неизвестный виновник не выдумывается");
    }
}
