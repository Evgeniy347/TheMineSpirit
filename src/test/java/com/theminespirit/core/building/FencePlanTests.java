package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class FencePlanTests {
    private static final int Y = 64;

    private static List<BlockCoord> square(int side) {
        return List.of(
                new BlockCoord(0, Y, 0),
                new BlockCoord(side, Y, 0),
                new BlockCoord(side, Y, side),
                new BlockCoord(0, Y, side));
    }

    @Test
    void contour_FourCorners_BuildsPerimeterWithoutGate() {
        FencePlan plan = FencePlan.contour(square(4));

        assertEquals(
                15,
                plan.cells().size(),
                "Периметр квадрата 5×5 — 16 клеток, одна уходит под калитку: иначе в заборе нет прохода");
        assertTrue(
                plan.cells().stream().allMatch(cell -> cell.y() == Y),
                "Забор стоит на одном уровне: наклонный забор дух не строит");
        assertFalse(
                plan.cells().contains(plan.gate()),
                "Клетка калитки не входит в забор: иначе проход окажется заложен блоком забора");
        assertEquals(
                new BlockCoord(2, Y, 0),
                plan.gate(),
                "Калитка стоит в середине длинной стороны: дух проходит там, где не надо ломать забор");
    }

    @Test
    void contour_Rectangle_GateOnLongestSide() {
        FencePlan plan = FencePlan.contour(List.of(
                new BlockCoord(0, Y, 0), new BlockCoord(8, Y, 0), new BlockCoord(8, Y, 2), new BlockCoord(0, Y, 2)));

        assertEquals(
                new BlockCoord(4, Y, 0),
                plan.gate(),
                "Калитка обязана быть в середине длинной стороны (8 клеток), а не короткой: иначе проход жмётся к углу");
        assertEquals(
                19, plan.cells().size(), "Периметр 9×3 — 20 клеток, минус калитка: каждая клетка ставится один раз");
    }

    @Test
    void contour_LShape_ConnectsEdges() {
        FencePlan plan = FencePlan.contour(List.of(
                new BlockCoord(0, Y, 0),
                new BlockCoord(4, Y, 0),
                new BlockCoord(4, Y, 3),
                new BlockCoord(2, Y, 3),
                new BlockCoord(2, Y, 6),
                new BlockCoord(0, Y, 6)));

        assertTrue(
                plan.cells().contains(new BlockCoord(1, Y, 0)),
                "Ребро вдоль оси X обязано заполняться промежуточными клетками: иначе между углами дыра");
        assertTrue(
                plan.cells().contains(new BlockCoord(4, Y, 2)),
                "Ребро вдоль оси Z обязано заполняться промежуточными клетками");
        assertTrue(
                plan.cells().contains(new BlockCoord(3, Y, 3)),
                "Горизонтальная полка Г-контура обязана быть непрерывной");
        assertEquals(
                plan.cells().size(),
                new HashSet<>(plan.cells()).size(),
                "Дубли в плане — двойной расход блоков: каждая клетка ставится один раз");
    }

    @Test
    void contour_ClosedInput_DoesNotDuplicateFirstCorner() {
        FencePlan closed = FencePlan.contour(List.of(
                new BlockCoord(0, Y, 0),
                new BlockCoord(4, Y, 0),
                new BlockCoord(4, Y, 4),
                new BlockCoord(0, Y, 4),
                new BlockCoord(0, Y, 0)));
        FencePlan open = FencePlan.contour(square(4));

        assertEquals(
                open,
                closed,
                "Замкнутый вход с повтором первого угла обязан давать тот же план: повтор угла не добавляет клеток");
    }

    @Test
    void contour_DiagonalEdge_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FencePlan.contour(
                        List.of(new BlockCoord(0, Y, 0), new BlockCoord(4, Y, 4), new BlockCoord(0, Y, 4))),
                "Диагональное ребро обязано отвергаться: забор по диагонали оставляет дыры между столбами");
    }

    @Test
    void contour_DifferentHeights_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FencePlan.contour(
                        List.of(new BlockCoord(0, Y, 0), new BlockCoord(4, Y, 0), new BlockCoord(4, Y + 1, 4))),
                "Углы на разной высоте обязаны отвергаться: забор стоит по земле, а не по склону");
    }

    @Test
    void contour_TooFewCorners_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FencePlan.contour(List.of(new BlockCoord(0, Y, 0), new BlockCoord(4, Y, 0))),
                "Двух углов недостаточно для замкнутого контура: получилась бы линия, а не забор");
    }
}
