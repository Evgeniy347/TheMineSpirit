package com.theminespirit.core.mining;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class Observe10Window implements SafetyView {
    private static final Path DIR = Path.of("src/test/resources/live/observe10");

    private final JsonNode json;
    private final CellWorld world = new CellWorld();

    private Observe10Window(JsonNode json) {
        this.json = json;
        for (JsonNode solid : json.get("solid")) {
            world.set(solid.get(0).asInt(), solid.get(1).asInt(), solid.get(2).asInt(), Cell.SOLID);
        }
    }

    static Observe10Window load(String file) throws IOException {
        JsonNode json = new ObjectMapper().readTree(Files.readString(DIR.resolve(file), StandardCharsets.UTF_8));
        return new Observe10Window(json);
    }

    BlockCoord spirit() {
        return coord(json.get("spirit"));
    }

    BlockCoord cell() {
        return coord(json.get("cell"));
    }

    BlockCoord target() {
        return coord(json.get("target"));
    }

    String occupantType() {
        return json.get("occupant").get("type").asText();
    }

    @Override
    public Cell cellAt(BlockCoord coord) {
        return world.cellAt(coord);
    }

    private static BlockCoord coord(JsonNode node) {
        return new BlockCoord(
                node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt());
    }
}
