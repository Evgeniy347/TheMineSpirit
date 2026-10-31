package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.engine.RunProgress;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScriptProgressWatchTests {
    private static final Path LIVE = Path.of("src/test/resources/live/observe11/rescue-progress.json");
    private static final int TICKS_PER_SECOND = 20;

    @Test
    void advancing_LiveDigProgressGrows_SkipsRescue() throws IOException {
        JsonNode live = new ObjectMapper().readTree(Files.readString(LIVE, StandardCharsets.UTF_8));
        UUID spirit = UUID.fromString(live.get("spirit").asText());
        ScriptProgressWatch watch = new ScriptProgressWatch();
        long now = 0;

        for (JsonNode sample : live.get("samples")) {
            now += sample.get("seconds").asLong() * TICKS_PER_SECOND;
            RunProgress progress = progress(live, sample.get("done").asInt());
            assertTrue(
                    watch.advancing(spirit, progress, now),
                    "Прогресс копки рос (done=" + sample.get("done").asInt() + " из "
                            + live.get("target").asInt() + "), но RescueWatch выдал спасение: дух копает, "
                            + "а не застрял");
        }
    }

    @Test
    void advancing_LiveProgressFrozen_AllowsRescueAfterStall() throws IOException {
        JsonNode live = new ObjectMapper().readTree(Files.readString(LIVE, StandardCharsets.UTF_8));
        UUID spirit = UUID.fromString(live.get("spirit").asText());
        ScriptProgressWatch watch = new ScriptProgressWatch();
        RunProgress frozen = progress(live, 88);

        assertTrue(watch.advancing(spirit, frozen, 0), "Первый замер прогресса считается свежим: спасать сразу нельзя");
        assertFalse(
                watch.advancing(spirit, frozen, TaskStall.STALL_TICKS + 1),
                "Прогресс не менялся дольше " + TaskStall.STALL_TICKS + " тиков: дух правда застрял, спасение нужно");
    }

    @Test
    void advancing_NoScriptProgress_AllowsRescue() throws IOException {
        JsonNode live = new ObjectMapper().readTree(Files.readString(LIVE, StandardCharsets.UTF_8));
        ScriptProgressWatch watch = new ScriptProgressWatch();

        assertFalse(
                watch.advancing(UUID.fromString(live.get("spirit").asText()), null, 0),
                "Без счётного прогресса скрипта сверять нечего: спасение застрявшего не блокируем");
    }

    private static RunProgress progress(JsonNode live, int done) {
        return new RunProgress(
                live.get("step").asInt(),
                live.get("totalSteps").asInt(),
                live.get("script").asText(),
                done,
                live.get("target").asInt());
    }
}
