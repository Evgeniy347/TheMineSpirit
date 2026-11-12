package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SlowSplitTests {
    @Test
    void mark_FastPhase_IsNotRecorded() {
        SlowSplit split = new SlowSplit();
        split.mark("dispatch", System.nanoTime());
        assertTrue(split.worst().isEmpty(), "быстрая фаза не засоряет сводку медленного тика");
    }

    @Test
    void mark_SlowPhase_KeepsWorst() {
        SlowSplit split = new SlowSplit();
        long millis = SlowSplit.SLOW_MILLIS + 50;
        split.mark("persist", System.nanoTime() - millis * 1_000_000);
        split.mark("persist", System.nanoTime() - SlowSplit.SLOW_MILLIS * 1_000_000);
        assertEquals(
                millis,
                split.worst().get("persist"),
                "сводка держит худшее время фазы: удержание тика вне пересборки (сервер 12.11: BoardService 1–2 с"
                        + " при пересборке 330 мс) иначе не найти");
    }
}
