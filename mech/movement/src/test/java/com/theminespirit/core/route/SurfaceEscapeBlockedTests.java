package com.theminespirit.core.route;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.movement.SurfaceEscape;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SurfaceEscapeBlockedTests {
    private static final BlockCoord START = new BlockCoord(0, 64, 0);
    private static final BlockCoord GAP = new BlockCoord(1, 64, 0);
    private static final BlockCoord EXIT = new BlockCoord(2, 64, 0);

    @Test
    void path_WithoutBlockedCells_UsesGapInWall() {
        Optional<List<BlockCoord>> found = SurfaceEscape.path(START, walled(), EXIT::equals, null);

        assertTrue(found.isPresent(), "Предусловие: в стене есть проход — путь к небу обязан найтись");
        assertTrue(found.orElseThrow().contains(GAP), "Предусловие: единственный проход — клетка " + GAP);
    }

    @Test
    void path_BlockedPlanCell_NotUsedEvenAsOnlyGap() {
        Optional<List<BlockCoord>> found =
                SurfaceEscape.path(START, walled(), EXIT::equals, null, cell -> cell.equals(GAP));

        assertFalse(
                found.isPresent(),
                "Путь спасения прошёл через клетку чужого плана " + GAP + ": дух лезет в постройку и ломает её "
                        + "(observe24: oak_stairs хижины (-177,68,-510) сломаны выходом)");
    }

    private static GridWorld walled() {
        return GridWorld.flat(64)
                .fill(-1, 64, -1, 1, 65, -1, GridWorld.DIRT)
                .fill(-1, 64, 1, 1, 65, 1, GridWorld.DIRT)
                .fill(-1, 64, 0, -1, 65, 0, GridWorld.DIRT)
                .fill(-1, 66, -1, 1, 66, 1, GridWorld.DIRT);
    }
}
