// lib: Из Baritone (github.com/cabaletta/baritone, pathing/path/SplicedPath.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NavPathSplicerTests {
    @Test
    void splice_ContiguousPaths_JoinAtSharedCell() {
        NavPath first = path(0, 5);
        NavPath second = path(5, 10);

        Optional<NavPath> spliced = NavPathSplicer.splice(first, second, false);

        assertTrue(spliced.isPresent(), "пути с общим концом склеиваются");
        assertEquals(11, spliced.get().length(), "общая клетка не дублируется");
        assertEquals(10, spliced.get().end().x(), "конец второго пути становится концом склейки");
    }

    @Test
    void splice_GapBetweenPaths_IsRejected() {
        assertTrue(
                NavPathSplicer.splice(path(0, 5), path(6, 10), false).isEmpty(),
                "пути с разрывом не склеиваются: между ними нет шага");
    }

    @Test
    void splice_OverlapNotAllowed_IsRejected() {
        NavPath first = path(0, 7);
        List<BlockCoord> cells = new ArrayList<>();
        cells.add(new BlockCoord(7, 1, 0));
        cells.add(new BlockCoord(6, 1, 0));
        cells.add(new BlockCoord(5, 1, 0));
        NavPath second = new NavPath(cells, 1, false);

        assertTrue(
                NavPathSplicer.splice(first, second, false).isEmpty(),
                "перекрытие без разрешения не склеивается: иначе путь пойдёт назад");
    }

    private static NavPath path(int fromX, int toX) {
        List<BlockCoord> cells = new ArrayList<>();
        for (int x = fromX; x <= toX; x++) {
            cells.add(new BlockCoord(x, 1, 0));
        }
        return new NavPath(cells, 1, false);
    }
}
