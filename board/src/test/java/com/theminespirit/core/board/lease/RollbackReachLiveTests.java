package com.theminespirit.core.board.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RollbackReachLiveTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void climbStand_HighBrokenCell_StandsAtTheCellAndNotAtTheLeaseBottom() throws IOException {
        World world = World.load("src/test/resources/live/undo-high-181-97-521.json");
        BlockCoord target = new BlockCoord(-181, 97, -521);
        BlockCoord leaseBottom = new BlockCoord(-172, 69, -516);

        Optional<BlockCoord> stand = RollbackReach.climbStand(target, world, 3);

        assertTrue(stand.isPresent(), "У клетки y=97 есть земля под столб: стоянка обязана найтись");
        BlockCoord found = stand.orElseThrow();
        assertTrue(found.y() < target.y(), "Столб строят снизу вверх: стоянка ниже клетки, найдено " + found);
        assertTrue(
                Math.abs(found.x() - target.x()) <= 3 && Math.abs(found.z() - target.z()) <= 3,
                "Столб строят у самой клетки отката, а не у низа аренды: стоянка " + found + " при клетке " + target);
        assertNotEquals(
                leaseBottom.x(),
                found.x(),
                "Низ чужой аренды (" + leaseBottom + ") — не место для столба к клетке " + target);
        assertNotEquals(leaseBottom.z(), found.z(), "Низ аренды в другой колонке: столб у него до клетки не достанет");
    }

    @Test
    void climbStand_BrokenCellOneAboveGround_StandsBesideOnSupportedGround() throws IOException {
        World world = World.load("src/test/resources/live/undo-base-172-69-516.json");
        BlockCoord target = new BlockCoord(-172, 69, -516);

        Optional<BlockCoord> stand = RollbackReach.climbStand(target, world, 3);

        assertTrue(stand.isPresent(), "Под сломанной клеткой y=69 есть земля: стоянка обязана найтись");
        BlockCoord found = stand.orElseThrow();
        assertEquals(68, found.y(), "Земля в этой колонке даёт ноги на y=68, а не в воздухе y=69: " + found);
        assertTrue(
                found.x() != target.x() || found.z() != target.z(),
                "Столб строят сбоку от клетки, иначе дух встанет в саму клетку отката: " + found);
        assertTrue(world.solid(found.x(), found.y() - 1, found.z()), "Под ногами стоянки обязана быть опора");
    }

    @Test
    void climbStand_NoGroundAround_IsEmpty() {
        BlockCoord target = new BlockCoord(0, 70, 0);

        Optional<BlockCoord> stand = RollbackReach.climbStand(target, (x, z) -> RollbackReach.Ground.NO_GROUND, 3);

        assertTrue(stand.isEmpty(), "Без земли вокруг столб не поставить: пустая стоянка честнее падения");
    }

    private record World(Map<BlockCoord, String> blocks, int minY, int maxY) implements RollbackReach.Ground {
        static World load(String file) throws IOException {
            JsonNode root = MAPPER.readTree(Files.readString(Path.of(file)));
            Map<BlockCoord, String> blocks = new HashMap<>();
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (JsonNode block : root.path("blocks")) {
                BlockCoord at = new BlockCoord(
                        block.path("x").asInt(),
                        block.path("y").asInt(),
                        block.path("z").asInt());
                blocks.put(at, block.path("block").asText());
                min = Math.min(min, at.y());
                max = Math.max(max, at.y());
            }
            return new World(Map.copyOf(blocks), min, max);
        }

        boolean solid(int x, int y, int z) {
            return blocks.containsKey(new BlockCoord(x, y, z));
        }

        @Override
        public int feet(int x, int z) {
            for (int y = maxY; y >= minY; y--) {
                if (solid(x, y, z) && !solid(x, y + 1, z) && !solid(x, y + 2, z)) {
                    return y + 1;
                }
            }
            return NO_GROUND;
        }
    }
}
