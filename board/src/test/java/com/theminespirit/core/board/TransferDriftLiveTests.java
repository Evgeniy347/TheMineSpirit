package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferDriftLiveTests {
    private static final Path DELIVER = Path.of("src/test/resources/live/observe12-deliver.json");

    @Test
    void observe_LiveDeliverReturnedToSamePlace_IsDrift() throws IOException {
        JsonNode live = new ObjectMapper().readTree(Files.readString(DELIVER, StandardCharsets.UTF_8));
        UUID carrier = UUID.fromString(live.get("carrier").asText());
        DriftWatch watch = new DriftWatch();
        Optional<DriftWatch.Drift> found = Optional.empty();
        int index = 0;
        for (JsonNode node : live.get("positions")) {
            Optional<DriftWatch.Drift> seen = watch.observe(carrier, coord(node), index * 100L);
            index++;
            if (seen.isPresent()) {
                found = seen;
                break;
            }
        }

        assertTrue(
                found.isPresent(),
                "spirit-3 по передаче " + live.get("ticket") + " прошёл z −595…−638 и вернулся в −595 "
                        + "(observe12 N17): колебание обязано ловиться, иначе маршрут к " + live.get("goal")
                        + " не перестраивается");
        assertTrue(
                found.orElseThrow().path() > found.orElseThrow().net() * 3,
                "Колебание — это длинный путь с малым смещением: путь "
                        + found.orElseThrow().path() + ", смещение "
                        + found.orElseThrow().net());
    }

    @Test
    void observe_StraightDelivery_IsNotDrift() {
        DriftWatch watch = new DriftWatch();
        UUID carrier = UUID.fromString("caf11c6f-9a9a-408d-92ed-71726c762c19");
        Optional<DriftWatch.Drift> found = Optional.empty();
        for (int step = 0; step < 12; step++) {
            found = watch.observe(carrier, new BlockCoord(-230, 55, -600 + step * 4), step * 100L);
        }

        assertTrue(
                found.isEmpty(),
                "Ровный путь к цели не колебание: иначе TransferWatch перестраивал бы каждый нормальный перенос");
    }

    private static BlockCoord coord(JsonNode node) {
        return new BlockCoord(
                node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt());
    }
}
