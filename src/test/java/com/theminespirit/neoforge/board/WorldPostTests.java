package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorldPostTests {
    @Test
    void handles_WorldCleanupNodes_AreRecognized() {
        assertTrue(WorldPost.handles("demolish_stray"), "Снос лишнего — узел мира");
        assertTrue(WorldPost.handles("backfill"), "Засыпка — узел мира");
        assertTrue(WorldPost.handles("clear_floating"), "Уборка висящего — узел мира");
        assertTrue(WorldPost.handles("level_lot"), "Выравнивание — узел мира");
        assertTrue(WorldPost.handles("restore_trace"), "Возврат следа — узел мира");
        assertTrue(WorldPost.handles("repair:hut"), "Ремонт постройки — узел мира");
        assertFalse(WorldPost.handles("collect_drops"), "Чужой узел не перехватывается");
    }

    @Test
    void post_WorldNodes_StayStubbed() {
        assertFalse(
                WorldPost.post(),
                "TODO(demolish_stray, backfill, clear_floating, level_lot, restore_trace, repair:<постройка>): "
                        + "действия ещё не реализованы, узел не ставит задание на доску");
    }
}
