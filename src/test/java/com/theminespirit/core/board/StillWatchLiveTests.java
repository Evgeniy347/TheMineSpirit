package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StillWatchLiveTests {
    private static final Path WATER = Path.of("src/test/resources/live/observe12-water-stuck.json");
    private static final Path BOTS = Path.of("src/test/resources/live/observe12-bots.json");
    private static final Path OSCILLATION = Path.of("src/test/resources/live/observe13-water-oscillation.json");
    private static final long WINDOW = 1200;

    @Test
    void still_LiveSpiritOneInWater_TriggersOnlyAfterWindow() throws IOException {
        JsonNode live = new ObjectMapper().readTree(Files.readString(WATER, StandardCharsets.UTF_8));
        UUID spirit = UUID.fromString(live.get("spirit").asText());
        BlockCoord feet = coord(live.get("feet"));
        StillWatch watch = new StillWatch();

        assertFalse(watch.still(spirit, feet, 0, WINDOW), "Первый замер позиции не может сразу считаться застоем");
        assertFalse(
                watch.still(spirit, feet, WINDOW - 1, WINDOW), "Окно " + WINDOW + " тиков ещё не прошло: спасать рано");
        assertTrue(
                watch.still(spirit, feet, WINDOW, WINDOW),
                "spirit-1 простоял в воде " + live.get("feet") + " больше " + live.get("still_ticks")
                        + " тиков (observe12), позиция не менялась: спасение обязано сработать по неподвижности, "
                        + "а не ждать замурованности");
    }

    @Test
    void still_LiveOscillationInSmallArea_CountsAsStill() throws IOException {
        JsonNode live = new ObjectMapper().readTree(Files.readString(OSCILLATION, StandardCharsets.UTF_8));
        UUID spirit = UUID.fromString(live.get("spirit").asText());
        StillWatch watch = new StillWatch();
        boolean still = false;
        for (int index = 0; index < live.get("positions").size(); index++) {
            still = watch.still(
                    spirit,
                    coord(live.get("positions").get(index)),
                    live.get("ticks").get(index).asLong(),
                    WINDOW);
        }

        assertTrue(
                still,
                "spirit-1 в observe13 колебался между " + live.get("positions").get(0) + " и "
                        + live.get("positions").get(1) + " (" + live.get("swim_stalled") + " SWIM_STALLED, спасений "
                        + live.get("rescue_events") + "): колебание в малом радиусе — это неподвижность, иначе "
                        + "смена клетки навсегда сбрасывает окно спасения");
    }

    @Test
    void still_SpiritLeavingArea_ResetsWindow() throws IOException {
        JsonNode bots = new ObjectMapper().readTree(Files.readString(BOTS, StandardCharsets.UTF_8));
        UUID spirit = UUID.fromString(bots.get("bots").get(0).get("id").asText());
        StillWatch watch = new StillWatch();
        BlockCoord first = coord(bots.get("bots").get(0).get("position"));
        BlockCoord moved = new BlockCoord(first.x() + StillWatch.AREA_RADIUS + 1, first.y(), first.z());

        assertFalse(watch.still(spirit, first, 0, WINDOW), "Первый замер не застой");
        assertFalse(watch.still(spirit, first, WINDOW - 1, WINDOW), "До окна спасения нет");
        assertFalse(
                watch.still(spirit, moved, WINDOW, WINDOW),
                "Дух вышел из области " + StillWatch.AREA_RADIUS + " блоков: окно неподвижности начинается заново, "
                        + "иначе спасение сработает на идущем духе");
        assertTrue(
                watch.still(spirit, moved, WINDOW * 2, WINDOW),
                "После выхода дух снова стоит окно: спасение обязано помнить новую область");
    }

    private static BlockCoord coord(JsonNode node) {
        return new BlockCoord(
                node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt());
    }
}
