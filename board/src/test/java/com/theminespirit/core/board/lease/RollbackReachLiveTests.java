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
import java.util.List;
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

        Optional<BlockCoord> stand =
                RollbackReach.climbStands(target, world, 3).stream().findFirst();

        assertTrue(
                stand.isPresent(),
                "У клетки y=97 есть земля под столб: стоянка обязана найтись"
                        + " Иначе тест climbStand_HighBrokenCell_StandsAtTheCellAndNotAtTheLeaseBottom перестанет ловить эту поломку. При смене climbStand_HighBrokenCell_StandsAtTheCellAndNotAtTheLeaseBottom проверь это утверждение.");
        BlockCoord found = stand.orElseThrow();
        assertTrue(
                found.y() < target.y(),
                "Столб строят снизу вверх: стоянка ниже клетки, найдено " + found
                        + " Иначе тест climbStand_HighBrokenCell_StandsAtTheCellAndNotAtTheLeaseBottom перестанет ловить эту поломку. При смене climbStand_HighBrokenCell_StandsAtTheCellAndNotAtTheLeaseBottom проверь это утверждение.");
        assertTrue(
                Math.abs(found.x() - target.x()) <= 3 && Math.abs(found.z() - target.z()) <= 3,
                "Столб строят у самой клетки отката, а не у низа аренды: стоянка " + found + " при клетке " + target
                        + " Иначе тест climbStand_HighBrokenCell_StandsAtTheCellAndNotAtTheLeaseBottom перестанет ловить эту поломку. При смене climbStand_HighBrokenCell_StandsAtTheCellAndNotAtTheLeaseBottom проверь это утверждение.");
        assertNotEquals(
                leaseBottom.x(),
                found.x(),
                "Низ чужой аренды (" + leaseBottom + ") — не место для столба к клетке " + target
                        + " Иначе тест climbStand_HighBrokenCell_StandsAtTheCellAndNotAtTheLeaseBottom перестанет ловить эту поломку. При смене climbStand_HighBrokenCell_StandsAtTheCellAndNotAtTheLeaseBottom проверь это утверждение.");
        assertNotEquals(
                leaseBottom.z(),
                found.z(),
                "Низ аренды в другой колонке: столб у него до клетки не достанет"
                        + " Иначе тест climbStand_HighBrokenCell_StandsAtTheCellAndNotAtTheLeaseBottom перестанет ловить эту поломку. При смене climbStand_HighBrokenCell_StandsAtTheCellAndNotAtTheLeaseBottom проверь это утверждение.");
    }

    @Test
    void climbStands_FlatGround_ListsSeveralStandsBesideTheCell() {
        BlockCoord target = new BlockCoord(0, 80, 0);

        List<BlockCoord> stands = RollbackReach.climbStands(target, (x, z) -> 64, 3);

        assertTrue(
                stands.size() > 1,
                "Первая стоянка может быть без пути: у клетки обязано быть несколько запасных, найдено " + stands
                        + " Иначе тест climbStands_FlatGround_ListsSeveralStandsBesideTheCell перестанет ловить эту поломку. При смене climbStands_FlatGround_ListsSeveralStandsBesideTheCell проверь это утверждение.");
        BlockCoord first = stands.get(0);
        assertTrue(
                first.x() != target.x() || first.z() != target.z(),
                "Ближнюю стоянку берут сбоку от клетки, а не в её колонке: " + first
                        + " Иначе тест climbStands_FlatGround_ListsSeveralStandsBesideTheCell перестанет ловить эту поломку. При смене climbStands_FlatGround_ListsSeveralStandsBesideTheCell проверь это утверждение.");
        assertEquals(
                1,
                Math.abs(first.x() - target.x()) + Math.abs(first.z() - target.z()),
                "Ближнюю боковую стоянку пробуют первой: " + first
                        + " Иначе тест climbStands_FlatGround_ListsSeveralStandsBesideTheCell перестанет ловить эту поломку. При смене climbStands_FlatGround_ListsSeveralStandsBesideTheCell проверь это утверждение.");
        assertTrue(
                stands.stream().allMatch(stand -> stand.y() < target.y()),
                "Столб строят снизу вверх: " + stands
                        + " Иначе тест climbStands_FlatGround_ListsSeveralStandsBesideTheCell перестанет ловить эту поломку. При смене climbStands_FlatGround_ListsSeveralStandsBesideTheCell проверь это утверждение.");
    }

    @Test
    void climbStand_BrokenCellOneAboveGround_StandsBesideOnSupportedGround() throws IOException {
        World world = World.load("src/test/resources/live/undo-base-172-69-516.json");
        BlockCoord target = new BlockCoord(-172, 69, -516);

        Optional<BlockCoord> stand =
                RollbackReach.climbStands(target, world, 3).stream().findFirst();

        assertTrue(
                stand.isPresent(),
                "Под сломанной клеткой y=69 есть земля: стоянка обязана найтись"
                        + " Иначе тест climbStand_BrokenCellOneAboveGround_StandsBesideOnSupportedGround перестанет ловить эту поломку. При смене climbStand_BrokenCellOneAboveGround_StandsBesideOnSupportedGround проверь это утверждение.");
        BlockCoord found = stand.orElseThrow();
        assertEquals(
                68,
                found.y(),
                "Земля в этой колонке даёт ноги на y=68, а не в воздухе y=69: " + found
                        + " Иначе тест climbStand_BrokenCellOneAboveGround_StandsBesideOnSupportedGround перестанет ловить эту поломку. При смене climbStand_BrokenCellOneAboveGround_StandsBesideOnSupportedGround проверь это утверждение.");
        assertTrue(
                found.x() != target.x() || found.z() != target.z(),
                "Столб строят сбоку от клетки, иначе дух встанет в саму клетку отката: " + found
                        + " При смене climbStand_BrokenCellOneAboveGround_StandsBesideOnSupportedGround проверь это утверждение.");
        assertTrue(
                world.solid(found.x(), found.y() - 1, found.z()),
                "Под ногами стоянки обязана быть опора"
                        + " Иначе тест climbStand_BrokenCellOneAboveGround_StandsBesideOnSupportedGround перестанет ловить эту поломку. При смене climbStand_BrokenCellOneAboveGround_StandsBesideOnSupportedGround проверь это утверждение.");
    }

    @Test
    void climbStand_NoGroundAround_IsEmpty() {
        BlockCoord target = new BlockCoord(0, 70, 0);

        Optional<BlockCoord> stand =
                RollbackReach.climbStands(target, (x, z) -> RollbackReach.Ground.NO_GROUND, 3).stream()
                        .findFirst();

        assertTrue(
                stand.isEmpty(),
                "Без земли вокруг столб не поставить: пустая стоянка честнее падения"
                        + " Иначе тест climbStand_NoGroundAround_IsEmpty перестанет ловить эту поломку. При смене climbStand_NoGroundAround_IsEmpty проверь это утверждение.");
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
