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

class WaterStepLiveTests {
    private static final Path LIVE = Path.of("src/test/resources/live/observe12-water-stuck.json");

    @Test
    void next_FlatStepIntoWater_SwimsInsteadOfWalking() throws IOException {
        JsonNode live = new ObjectMapper().readTree(Files.readString(LIVE, StandardCharsets.UTF_8));
        BlockCoord feet = coord(live.get("feet"));
        BlockCoord step = coord(live.get("target_step"));

        WalkAscent.Command command = WalkAscent.next(
                step,
                new WalkAscent.Pose(feet, feet.x() + 0.5, feet.y(), feet.z() + 0.5),
                new WalkAscent.Body(true, true, false, true));

        assertEquals(
                WalkAscent.Step.SWIM,
                command.step(),
                "В observe12 spirit-1 стоял в воде " + live.get("feet")
                        + " и шаг в воду " + live.get("target_step") + " кончался NO_PROGRESS: вода — это не "
                        + "ровный шаг, дух обязан плыть (подъём + движение), иначе маршрут в пещере не начинается");
    }

    @Test
    void next_FlatDryStep_Walks() {
        BlockCoord feet = new BlockCoord(0, 64, 0);
        WalkAscent.Command command = WalkAscent.next(
                new BlockCoord(1, 64, 0),
                new WalkAscent.Pose(feet, 0.5, 64.0, 0.5),
                new WalkAscent.Body(true, false, false, false));

        assertEquals(
                WalkAscent.Step.MOVE,
                command.step(),
                "Сухой ровный шаг остаётся ходьбой: иначе каждый шаг по земле начнёт всплывать");
    }

    private static BlockCoord coord(JsonNode node) {
        return new BlockCoord(
                node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt());
    }
}
