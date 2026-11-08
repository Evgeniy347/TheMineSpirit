// lib: Из Baritone (github.com/cabaletta/baritone, utils/pathing/Favoring.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NavFavoringTests {
    @Test
    void of_PreviousPath_ReducesOnlyItsCells() {
        NavPath previous = path(0, 3, 5);
        NavFavoring favoring = NavFavoring.of(previous, 0.5);

        assertEquals(0.5, favoring.factor(0, 3, 0), 0, "клетка прошлого пути дешевле");
        assertEquals(0.5, favoring.factor(5, 3, 0), 0, "конец прошлого пути тоже дешевле");
        assertEquals(1.0, favoring.factor(9, 3, 0), 0, "новая клетка стоит обычную цену");
        assertFalse(favoring.isEmpty(), "прошлый путь не пуст");
        assertTrue(NavFavoring.EMPTY.isEmpty(), "пустое favoring не меняет цены");
    }

    @Test
    void of_NonPositiveFactor_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> NavFavoring.of(path(0, 3, 0), 0),
                "нулевой множитель сделал бы клетку бесплатной и сломал A*");
    }

    @Test
    void favoring_SymmetricWall_ChoosesFavoredSide() {
        NavTestWorld world = NavTestWorld.flat(0);
        for (int z = -2; z <= 2; z++) {
            world.solid(4, 1, z).solid(4, 2, z);
        }
        List<BlockCoord> side = new ArrayList<>();
        for (int x = 0; x <= 8; x++) {
            side.add(new BlockCoord(x, 1, 3));
        }
        NavPath previous = new NavPath(side, 0, false);
        NavContext context = new NavContext(world, NavSettings.defaults(), 64);

        NavResult result =
                NavAStarTestRun.plan(context, 0, 1, 0, new NavGoalBlock(8, 1, 0), NavFavoring.of(previous, 0.5));

        assertTrue(
                result.path().positions().stream().anyMatch(cell -> cell.z() == 3),
                "при равных обходах выбирается тот, что дешевле по прошлому пути: "
                        + result.path().positions());
    }

    private static NavPath path(int fromX, int y, int toX) {
        List<BlockCoord> cells = new ArrayList<>();
        for (int x = fromX; x <= toX; x++) {
            cells.add(new BlockCoord(x, y, 0));
        }
        return new NavPath(cells, 1, false);
    }
}
