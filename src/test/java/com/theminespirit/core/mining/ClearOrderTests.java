package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClearOrderTests {
    @Test
    void topmost_HillWithRuinOnTop_OnlyTopCellsOfEachColumn() {
        BlockCoord ground = new BlockCoord(0, 0, 0);
        BlockCoord hill = new BlockCoord(0, 1, 0);
        BlockCoord ruin = new BlockCoord(0, 2, 0);
        BlockCoord air = new BlockCoord(0, 3, 0);
        BlockCoord side = new BlockCoord(1, 0, 0);
        Set<BlockCoord> solid = Set.of(ground, hill, ruin, side);
        List<BlockCoord> picked = ClearOrder.topmost(List.of(ground, side, hill, ruin, air), solid::contains);
        assertEquals(
                List.of(side, ruin),
                picked,
                "Расчистка сверху вниз: из колонки берётся верхний стоящий блок (стенка на холме), иначе срезка "
                        + "оставляет его висеть без стоянки; воздух целью не бывает");
    }
}
