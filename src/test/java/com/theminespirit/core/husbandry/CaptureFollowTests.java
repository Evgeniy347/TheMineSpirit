package com.theminespirit.core.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.husbandry.RingGround.Cell;
import org.junit.jupiter.api.Test;

class CaptureFollowTests {
    @Test
    void leadingCell_AnimalMovedEast_LeadsEastByWallReach() {
        Cell previous = new Cell(4, 4);

        Cell leading = CaptureFollow.leadingCell(new Cell(5, 4), previous);

        assertEquals(new Cell(7, 4), leading,
                "Ведомая клетка обязана быть на расстоянии стенки впереди животного: дух строит стенку перед "
                        + "зверем и отрезает его; клетка в один шаг попадает во внутренность кольца, и ближней "
                        + "к ней оказывается боковая стенка — овца уходит в незакрытую дальнюю сторону");
    }

    @Test
    void leadingCell_AnimalMovedDiagonally_LeadsDiagonally() {
        Cell leading = CaptureFollow.leadingCell(new Cell(5, 6), new Cell(4, 5));

        assertEquals(new Cell(7, 8), leading,
                "Диагональный шаг животного ведёт стенку по обеим осям: иначе зверь проскакивает угол между "
                        + "двумя стенками");
    }

    @Test
    void leadingCell_AnimalStands_LeadsToOwnCell() {
        Cell standing = new Cell(4, 4);

        Cell leading = CaptureFollow.leadingCell(standing, standing);

        assertEquals(standing, leading,
                "Стоящее животное не задаёт направления: дух обязан строить ближнюю стенку, а не выдумывать "
                        + "сторону движения");
    }

    @Test
    void leadingCell_NoPreviousObservation_LeadsToOwnCell() {
        Cell only = new Cell(4, 4);

        Cell leading = CaptureFollow.leadingCell(only, null);

        assertEquals(only, leading,
                "Без прошлого замера направления нет: первая стенка строится по текущей клетке, иначе дух "
                        + "кидается в случайную сторону");
    }

    @Test
    void ringCovers_AnimalInsideInterior_True() {
        RingPlan.Ring ring = RingPlan.plan(RingGround.flat(-2, -2, 6, 6, 10), new Cell(0, 0), RingPlan.Size.LARGE);

        assertTrue(CaptureFollow.ringCovers(ring, new Cell(1, 1)),
                "Клетка внутри кольца обязана считаться покрытой: по этому признаку дух решает, что план ещё "
                        + "годится и пересчитывать его не нужно");
    }

    @Test
    void ringCovers_AnimalLeftInterior_False() {
        RingPlan.Ring ring = RingPlan.plan(RingGround.flat(-2, -2, 6, 6, 10), new Cell(0, 0), RingPlan.Size.LARGE);

        assertFalse(CaptureFollow.ringCovers(ring, new Cell(3, 0)),
                "Ушедшая из внутренности клетка обязана считаться непокрытой: тогда дух пересчитывает кольцо "
                        + "вокруг зверя, а не бросает отлов с 'животное ушло'");
    }
}