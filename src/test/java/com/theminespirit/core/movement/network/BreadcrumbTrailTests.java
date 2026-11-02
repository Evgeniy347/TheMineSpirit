package com.theminespirit.core.movement.network;

import static com.theminespirit.core.movement.network.GridWorld.cell;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class BreadcrumbTrailTests {
    @Test
    void backtrack_MoreCellsThanKeep_ReturnsNewestFirst() {
        BreadcrumbTrail trail = new BreadcrumbTrail(5);
        for (int x = 0; x <= 6; x++) {
            trail.remember(cell(x, 1, 0));
        }

        assertEquals(
                List.of(cell(6, 1, 0), cell(5, 1, 0), cell(4, 1, 0), cell(3, 1, 0), cell(2, 1, 0)),
                trail.backtrack(),
                "обратный маршрут — последние клетки в обратном порядке: по нему дух возвращается домой");
        assertEquals(5, trail.size(), "след помнит только последние клетки, иначе память растёт без предела");
    }

    @Test
    void remember_SameCellTwice_StoresOne() {
        BreadcrumbTrail trail = new BreadcrumbTrail(10);
        trail.remember(cell(1, 1, 1));
        trail.remember(cell(1, 1, 1));
        trail.remember(cell(2, 1, 1));

        assertEquals(
                List.of(cell(1, 1, 1), cell(2, 1, 1)), trail.forward(), "стояние на месте не плодит одинаковые крошки");
    }

    @Test
    void keep_NotPositive_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BreadcrumbTrail(0),
                "след нулевой длины бесполезен: возвращаться некуда");
    }

    @Test
    void forward_EmptyTrail_ReturnsEmptyList() {
        assertEquals(List.of(), new BreadcrumbTrail(3).forward(), "пустой след — пустой маршрут, без исключений");
    }

    private static BlockCoord cell(int x, int y, int z) {
        return GridWorld.cell(x, y, z);
    }
}
