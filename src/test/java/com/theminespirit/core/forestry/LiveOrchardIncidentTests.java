package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LiveOrchardIncidentTests {
    private static final Path INCIDENT = Path.of("src/test/resources/live/observe10-orchard-incident.json");

    @Test
    void liveIncident_OakLogNearSettlement_IsPlacedLogAndNotATree() throws IOException {
        JsonNode incident = new ObjectMapper().readTree(read(INCIDENT));
        JsonNode failure = incident.get("failure");
        Map<BlockCoord, String> blocks = blocks(incident);
        BlockCoord log = new BlockCoord(-170, 70, -506);

        assertEquals(
                "minecraft:oak_log",
                blocks.get(log),
                "Снимок живого мира обязан содержать бревно, которое детектор отверг в observe10");
        assertTrue(failure.get("rejectedNotTrees").asInt() > 0, "Живой провал говорит об отвергнутых группах брёвен");

        TreeDetection detection = TreeShape.detect(log, new SnapshotView(blocks));

        NotATree rejected = assertInstanceOf(
                NotATree.class,
                detection,
                "Бревно в " + log + " лежит в воздухе без кроны и ствола — это поставленный блок, а не дерево; "
                        + "детектор обязан отвергнуть его, иначе дух срубит постройку вместо леса");
        assertEquals(
                NotATree.Reason.NO_SOIL,
                rejected.reason(),
                "Отказ живому бревну обязан быть «под ним нет почвы, а воздух»: так видно, что это не ствол дерева");
    }

    private static Map<BlockCoord, String> blocks(JsonNode incident) {
        Map<BlockCoord, String> blocks = new HashMap<>();
        for (JsonNode entry : incident.get("blocks")) {
            blocks.put(
                    new BlockCoord(
                            entry.get("x").asInt(),
                            entry.get("y").asInt(),
                            entry.get("z").asInt()),
                    entry.get("block").asText());
        }
        return blocks;
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private record SnapshotView(Map<BlockCoord, String> blocks) implements TreeView {
        @Override
        public boolean isLog(BlockCoord coord) {
            String block = blocks.get(coord);
            return block != null && block.endsWith("_log");
        }

        @Override
        public boolean isNaturalLeaves(BlockCoord coord) {
            String block = blocks.get(coord);
            return block != null && block.endsWith("_leaves");
        }

        @Override
        public boolean isSoil(BlockCoord coord) {
            String block = blocks.get(coord);
            return block != null && (block.endsWith("_dirt") || "minecraft:grass_block".equals(block));
        }

        @Override
        public boolean isArtificial(BlockCoord coord) {
            String block = blocks.get(coord);
            return block != null
                    && (block.endsWith("_planks")
                            || block.endsWith("_fence")
                            || block.endsWith("_torch")
                            || "minecraft:crafting_table".equals(block));
        }

        @Override
        public boolean isEmpty(BlockCoord coord) {
            String block = blocks.get(coord);
            return block == null || "minecraft:air".equals(block);
        }
    }
}
