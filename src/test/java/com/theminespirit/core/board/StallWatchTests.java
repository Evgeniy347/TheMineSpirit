package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StallWatchTests {
    private static final Path LIVE = Path.of("src/test/resources/live/observe11/mine-stall.json");

    @Test
    void stalled_LiveMineFrozenAtZero_ReportsBeforeBoardCancel() throws IOException {
        JsonNode live = new ObjectMapper().readTree(Files.readString(LIVE, StandardCharsets.UTF_8));
        long frozen = live.get("frozenTicks").asLong();

        StallWatch watch = new StallWatch();
        long reportedAt = -1;
        for (long tick = 0; tick <= frozen; tick++) {
            if (watch.stalled(tick, 0L)) {
                reportedAt = tick;
                break;
            }
        }

        assertTrue(
                reportedAt > 0,
                "Дух стоял " + frozen + " тиков на " + live.get("feet") + " без прогресса ("
                        + live.get("outcome").asText() + "), а отчёт о застое не напечатался: доска отменяет "
                        + "скрипт на том же тике");
        assertTrue(
                reportedAt < TaskStall.NO_PROGRESS_TICKS,
                "Отчёт о застое обязан печататься раньше отмены доской: порог " + TaskStall.NO_PROGRESS_TICKS
                        + " тиков, а отчёт пришёл на " + reportedAt);
    }

    @Test
    void stalled_ProgressSignatureGrows_ResetsTimer() {
        StallWatch watch = new StallWatch();

        assertFalse(watch.stalled(0, 1L), "Первый замер прогресса не может сразу считаться застоем");
        assertFalse(
                watch.stalled(TaskStall.NO_PROGRESS_TICKS + 1, 2L),
                "Сдвиг подписи прогресса сбрасывает застой: шахта копает, это не молчаливое стояние");
    }
}
