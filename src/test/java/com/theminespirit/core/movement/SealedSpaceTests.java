package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SealedSpaceTests {
    private static final BlockCoord FEET = new BlockCoord(0, 60, 0);

    @Test
    void sealed_UnderwaterPocketWithSurfaceAbove_NotSealed() {
        Set<BlockCoord> water = Set.of(FEET, new BlockCoord(0, 61, 0), new BlockCoord(0, 62, 0));
        BlockCoord surface = new BlockCoord(0, 63, 0);

        assertFalse(
                SealedSpace.sealed(FEET, view(Set.of(surface), water, Set.of(surface))),
                "Дух в воде под открытым небом не замурован: он всплывает, а не копает лестницу; "
                        + "иначе спасение срабатывает при каждом заплыве и зацикливается");
    }

    @Test
    void sealed_StonePocketWithoutSky_IsSealed() {
        assertTrue(
                SealedSpace.sealed(FEET, view(Set.of(FEET), Set.of(), Set.of())),
                "Карман из камня без единого выхода к небу — настоящая замуровка: духу требуется лестница");
    }

    @Test
    void sealed_WaterCorridorToSky_NotSealed() {
        Set<BlockCoord> water = Set.of(FEET, new BlockCoord(2, 60, 0), new BlockCoord(2, 61, 0));
        Set<BlockCoord> air = Set.of(new BlockCoord(1, 60, 0), new BlockCoord(1, 61, 0), new BlockCoord(2, 62, 0));
        BlockCoord surface = new BlockCoord(2, 62, 0);

        assertFalse(
                SealedSpace.sealed(FEET, view(air, water, Set.of(surface))),
                "Вода — дорога: подводный ход, ведущий к небу, не делает духа замурованным");
    }

    private static SealedSpace.View view(Set<BlockCoord> air, Set<BlockCoord> water, Set<BlockCoord> sky) {
        return new SealedSpace.View() {
            @Override
            public boolean passable(BlockCoord cell) {
                return air.contains(cell);
            }

            @Override
            public boolean swimmable(BlockCoord cell) {
                return water.contains(cell);
            }

            @Override
            public boolean sky(BlockCoord cell) {
                return sky.contains(cell);
            }
        };
    }
}
