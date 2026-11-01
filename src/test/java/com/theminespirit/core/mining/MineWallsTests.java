package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MineWallsTests {
    @Test
    void of_PassageAndFloorSides_BothAreWalls() {
        MineColumn column = new MineColumn(new BlockCoord(0, 64, 0), 3);
        BlockCoord floorSide = new BlockCoord(1, 64, 0);
        BlockCoord passageSide = new BlockCoord(1, 65, 0);

        List<BlockCoord> walls = MineWalls.of(column, Set.of(floorSide, passageSide));

        assertTrue(
                walls.contains(floorSide),
                "Стенка на уровне пола тоже ставится: без неё 56 клеток @floor у входа остаются воздухом и "
                        + "шахта висит на 96%: " + walls);
        assertTrue(walls.contains(passageSide), "Стенки вдоль прохода тоже остаются в списке: " + walls);
    }
}
