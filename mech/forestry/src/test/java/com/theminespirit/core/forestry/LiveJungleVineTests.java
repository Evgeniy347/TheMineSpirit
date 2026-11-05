package com.theminespirit.core.forestry;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LiveJungleVineTests {
    private static final Path WORLD = Path.of("src/test/resources/live/observe11-jungle-tree.json");
    private static final BlockCoord BASE = new BlockCoord(-203, 70, -565);

    @Test
    void liveJungleTree_VinesOnStandCells_AreClearedSoSpiritReachesTrunk() throws IOException {
        JsonNode incident = new ObjectMapper().readTree(read(WORLD));
        SnapshotView view = new SnapshotView(blocks(incident));
        TreeDetection detection = TreeShape.detect(BASE, view);
        Tree tree = assertInstanceOf(
                Tree.class, detection, "Живой ствол обязан разобраться как дерево, а вышло " + detection);

        List<BlockCoord> clearing = LeafObstacles.blockingStand(tree, view, BASE);

        assertTrue(
                clearing.contains(new BlockCoord(-203, 70, -564)),
                "Лианы вплотную к стволу закрывают стоянку и не попадают в план расчистки: " + clearing
                        + ". В observe11 такие лианы дали «без стоянки: 43» — дух не доходит до ствола");
        assertTrue(
                clearing.contains(new BlockCoord(-202, 70, -565)),
                "Лиана в клетке стоянки — такая же преграда, как листва: её тоже надо ломать при рубке: " + clearing);
        assertTrue(
                !clearedStands(tree, view, clearing).isEmpty(),
                "После расчистки листвы и лиан стоянка у ствола обязана появиться, иначе дерево так и останется "
                        + "недостижимым");
    }

    private static List<BlockCoord> clearedStands(Tree tree, SnapshotView view, List<BlockCoord> clearing) {
        Set<BlockCoord> cleared = Set.copyOf(clearing);
        return ChopStands.stands(
                tree,
                feet -> free(view, cleared, feet)
                        && free(view, cleared, new BlockCoord(feet.x(), feet.y() + 1, feet.z()))
                        && view.isSoil(new BlockCoord(feet.x(), feet.y() - 1, feet.z())));
    }

    private static boolean free(SnapshotView view, Set<BlockCoord> cleared, BlockCoord coord) {
        return cleared.contains(coord) || view.isEmpty(coord);
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
            return block != null && (block.endsWith("_log") || block.endsWith("_wood"));
        }

        @Override
        public boolean isNaturalLeaves(BlockCoord coord) {
            String block = blocks.get(coord);
            return block != null && block.endsWith("_leaves");
        }

        @Override
        public boolean isFoliageObstacle(BlockCoord coord) {
            String block = blocks.get(coord);
            return block != null && (block.endsWith("_leaves") || block.endsWith(":vine") || block.endsWith("_vines"));
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
            return block == null
                    || "minecraft:air".equals(block)
                    || "minecraft:short_grass".equals(block)
                    || "minecraft:fern".equals(block);
        }
    }
}
