// lib: Из Baritone (github.com/cabaletta/baritone, pathing/path/CutoffPath.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NavPathCutoffTests {
    @Test
    void atAvailable_FirstUnloadedChunk_TrimsBeforeIt() {
        NavPath path = path(0, 20, 1);
        NavTestWorld world = NavTestWorld.flat(0).unloadFrom(1);

        NavPath cut = NavPathCutoff.atAvailable(path, world);

        assertEquals(16, cut.length(), "путь обрезан перед незагруженным чанком");
        assertEquals(15, cut.end().x(), "последняя клетка загруженного чанка");
    }

    @Test
    void staticCutoff_GoalNotReached_KeepsConfiguredShare() {
        NavPath path = path(0, 20, 1);
        NavPath cut = NavPathCutoff.staticCutoff(path, new NavGoalBlock(100, 1, 0), 6, 0.5);

        assertEquals(13, cut.length(), "длина = min + (длина - min) * доля");
    }

    @Test
    void staticCutoff_GoalReached_LeavesPathWhole() {
        NavPath path = path(0, 20, 1);
        NavPath cut = NavPathCutoff.staticCutoff(path, new NavGoalBlock(20, 1, 0), 6, 0.5);

        assertEquals(path.length(), cut.length(), "дошедший до цели путь не режется");
    }

    @Test
    void staticCutoff_InvalidShare_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> NavPathCutoff.staticCutoff(path(0, 20, 1), new NavGoalBlock(100, 1, 0), 6, 0),
                "нулевая доля не оставляла бы пути");
    }

    private static NavPath path(int fromX, int toX, int y) {
        List<BlockCoord> cells = new ArrayList<>();
        for (int x = fromX; x <= toX; x++) {
            cells.add(new BlockCoord(x, y, 0));
        }
        return new NavPath(cells, 1, false);
    }
}
