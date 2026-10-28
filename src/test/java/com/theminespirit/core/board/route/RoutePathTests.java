package com.theminespirit.core.board.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RoutePathTests {
    private static final BlockCoord FROM = new BlockCoord(0, 64, 0);
    private static final BlockCoord TO = new BlockCoord(10, 64, 4);

    @Test
    void tiles_FromSettlementToPoint_BothEndsInside() {
        List<BlockCoord> tiles = RoutePath.tiles(FROM, TO);
        Set<BlockCoord> cells = Set.copyOf(tiles);

        assertTrue(cells.contains(FROM), "Тропа начинается у поселения");
        assertTrue(cells.contains(TO), "Тропа доходит до точки биома");
    }

    @Test
    void tiles_AlongRoute_AreThreeWideAndUnique() {
        List<BlockCoord> tiles = RoutePath.tiles(FROM, TO);

        assertEquals(tiles.size(), Set.copyOf(tiles).size(), "Клетки тропы не повторяются: лишний проход не нужен");
        Set<Integer> zAtStart = tiles.stream()
                .filter(cell -> cell.x() == 0 && cell.z() >= -1 && cell.z() <= 1)
                .map(BlockCoord::z)
                .collect(Collectors.toSet());
        assertEquals(Set.of(-1, 0, 1), zAtStart, "Дорожка трёхклеточной ширины: дух не идёт по кромке");
    }

    @Test
    void tiles_SamePoint_SingleColumn() {
        List<BlockCoord> tiles = RoutePath.tiles(FROM, FROM);

        assertTrue(tiles.contains(FROM), "Точка совпала с поселением — тропа всё равно отмечает место");
    }
}
