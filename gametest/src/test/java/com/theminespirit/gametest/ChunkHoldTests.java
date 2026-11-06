package com.theminespirit.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ChunkHoldTests {
    @Test
    void releasable_ChunkOfNextBatchTest_StaysForced() {
        ChunkHold hold = new ChunkHold(List.of(1L, 2L, 3L, 4L));

        assertEquals(
                List.of(1L, 4L),
                hold.releasable(Set.of(2L, 3L, 9L)),
                "Сцена в конце отпускает только свои чанки, не нужные живым сценам: GameTestRunner ставит "
                        + "следующую пачку раньше, чем слушатель сцены отпускает чанки, и отпущенный чанк чужой "
                        + "структуры не тикает — следующая пачка не стартует и висит до стопа по времени "
                        + "(после провала first_stage). Перепроверь GameTestChunks.liveTestChunks");
    }

    @Test
    void releasable_NoLiveTests_ReleasesAllHeld() {
        ChunkHold hold = new ChunkHold(List.of(5L, 6L));

        assertEquals(
                List.of(5L, 6L),
                hold.releasable(Set.of()),
                "Без живых сцен сцена отпускает все чанки, что сама взяла: иначе принудительная загрузка "
                        + "копится от сцены к сцене и мир прогона растёт");
    }
}
