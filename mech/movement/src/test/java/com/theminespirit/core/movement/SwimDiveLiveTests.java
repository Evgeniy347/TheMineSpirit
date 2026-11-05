package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SwimDiveLiveTests {
    private static final Path LIVE = Path.of("src/test/resources/live/waterstuck/dive.json");

    @Test
    void next_WaterStepBelowFeet_DivesInsteadOfSurfacing() throws IOException {
        JsonNode live = new ObjectMapper().readTree(Files.readString(LIVE, StandardCharsets.UTF_8));
        BlockCoord feet = coord(live.get("feet"));
        BlockCoord target = coord(live.get("target"));

        WalkAscent.Command command = WalkAscent.next(
                target,
                new WalkAscent.Pose(feet, feet.x() + 0.5, feet.y(), feet.z() + 0.5),
                new WalkAscent.Body(false, false, false, true));

        assertEquals(
                WalkAscent.Step.DIVE,
                command.step(),
                "В observe14 маршрут вёл из " + feet + " в воду " + target + " на 4 блока ниже, а дух всплывал "
                        + "(SWIM + jump) и падал: шаг вниз обязан быть погружением, иначе он никогда не дойдёт до выхода");
    }

    private static BlockCoord coord(JsonNode node) {
        return new BlockCoord(
                node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt());
    }
}
