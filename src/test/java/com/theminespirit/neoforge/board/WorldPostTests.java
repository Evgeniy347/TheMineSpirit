package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorldPostTests {
    @Test
    void handles_WorldCleanupAndRepairNodes_AreRecognized() {
        assertTrue(WorldPost.handles("demolish_stray"), "Снос лишнего — узел мира");
        assertTrue(WorldPost.handles("backfill"), "Засыпка — узел мира");
        assertTrue(WorldPost.handles("clear_floating"), "Уборка висящего — узел мира");
        assertTrue(WorldPost.handles("level_lot"), "Выравнивание — узел мира");
        assertTrue(WorldPost.handles("restore_trace"), "Возврат следа — узел мира");
        assertTrue(WorldPost.handles("repair:hut"), "Ремонт хижины — узел мира");
        assertTrue(WorldPost.handles("repair:clearing"), "Ремонт расчистки — узел мира");
        assertTrue(WorldPost.handles("repair:mine"), "Ремонт шахты — узел мира");
        assertFalse(WorldPost.handles("collect_drops"), "Чужой узел не перехватывается");
    }
}
