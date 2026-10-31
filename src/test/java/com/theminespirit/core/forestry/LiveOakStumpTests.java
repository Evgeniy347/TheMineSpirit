package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LiveOakStumpTests {
    private static final Path WORLD = Path.of("src/test/resources/live/woodreach/oak-stumps.json");

    @Test
    void liveStumpField_Stumps_AreNotTreesButFarOakIs() throws IOException {
        JsonNode world = new ObjectMapper().readTree(Files.readString(WORLD, StandardCharsets.UTF_8));
        SnapshotView view = new SnapshotView(blocks(world));
        for (JsonNode stump : world.get("stumps")) {
            BlockCoord base = coord(stump.get("base"));
            assertInstanceOf(
                    NotATree.class,
                    TreeShape.detect(base, view),
                    "Живой пень " + base + " обязан быть отвергнут как не-дерево: иначе дух снова бьётся в "
                            + "недоступный остаток вместо настоящего дуба");
        }
        TreeDetection detected = TreeShape.detect(coord(world.get("tree").get("base")), view);
        Tree tree = assertInstanceOf(
                Tree.class, detected, "Ближайший настоящий дуб обязан разбираться как дерево, а вышло " + detected);
        BlockCoord anchor = coord(world.get("anchor"));
        int distance = (int)
                Math.ceil(Math.hypot(tree.base().x() - anchor.x(), tree.base().z() - anchor.z()));
        assertTrue(distance > 32, "Живой настоящий дуб обязан быть дальше старого предела 32: " + distance);
        Optional<BlockCoord> near = TreeChooser.near(
                anchor,
                ChopTuning.maxDistance(CatalogLimits.CHOP_RADIUS_MAX),
                tree.base().x(),
                tree.base().y(),
                tree.base().z(),
                List.of(tree.base()),
                Set.of());
        assertTrue(
                near.isPresent(),
                "Настоящий дуб в " + distance + " блоках обязан попадать в поиск доски в 100: иначе пни снова "
                        + "останутся единственными целями");
    }

    private static Map<BlockCoord, String> blocks(JsonNode world) {
        Map<BlockCoord, String> blocks = new HashMap<>();
        for (JsonNode entry : world.get("tree").get("blocks")) {
            blocks.put(
                    new BlockCoord(
                            entry.get("x").asInt(),
                            entry.get("y").asInt(),
                            entry.get("z").asInt()),
                    entry.get("block").asText());
        }
        for (JsonNode stump : world.get("stumps")) {
            BlockCoord base = coord(stump.get("base"));
            blocks.put(base, stump.get("block").asText());
            if (!stump.get("below").isNull()) {
                blocks.put(
                        new BlockCoord(base.x(), base.y() - 1, base.z()),
                        stump.get("below").asText());
            }
        }
        return blocks;
    }

    private static BlockCoord coord(JsonNode node) {
        return new BlockCoord(
                node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt());
    }

    private record SnapshotView(Map<BlockCoord, String> blocks) implements TreeView {
        @Override
        public boolean isLog(BlockCoord coord) {
            String block = blocks.get(coord);
            return block != null && (block.endsWith("_log") || block.endsWith("_wood"));
        }

        @Override
        public boolean isNaturalLeaves(BlockCoord coord) {
            String block = blocks.get(coord);
            return block != null && block.endsWith("_leaves");
        }

        @Override
        public boolean isSoil(BlockCoord coord) {
            String block = blocks.get(coord);
            return block != null
                    && (block.endsWith("_dirt")
                            || "minecraft:dirt".equals(block)
                            || "minecraft:grass_block".equals(block));
        }

        @Override
        public boolean isArtificial(BlockCoord coord) {
            return false;
        }

        @Override
        public boolean isEmpty(BlockCoord coord) {
            String block = blocks.get(coord);
            return block == null || "minecraft:air".equals(block);
        }
    }
}
