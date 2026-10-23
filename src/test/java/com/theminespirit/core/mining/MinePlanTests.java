package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MinePlanTests {
    private static final int STEPS = 5;

    private static List<BlockCoord> descentFloors() {
        List<BlockCoord> floors = new ArrayList<>();
        for (int k = 0; k < STEPS; k++) {
            floors.add(new BlockCoord(k, -k, 0));
        }
        return floors;
    }

    private static List<BlockCoord> descentAir() {
        List<BlockCoord> air = new ArrayList<>();
        for (int k = 0; k < STEPS; k++) {
            for (int dy = 1; dy <= 3; dy++) {
                air.add(new BlockCoord(k, -k + dy, 0));
            }
        }
        return air;
    }

    @Test
    void columns_Descent_OneColumnPerStepThreeHigh() {
        List<MineColumn> columns = MinePlan.columns(descentFloors(), descentAir());

        assertEquals(STEPS, columns.size(), "каждая ступень спуска — своя колонка: пол и три клетки воздуха");
        assertTrue(
                columns.stream().allMatch(column -> column.height() == MineColumn.PASSAGE_HEIGHT),
                "проход спуска 3 блока высотой — дух идёт вверх и вниз с прыжком; фактически " + columns);
    }

    @Test
    void columns_AirWithoutFloor_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MinePlan.columns(List.of(new BlockCoord(0, 0, 0)), List.of(new BlockCoord(5, 1, 0))),
                "воздух без пола под ним — ошибка схемы: дух не встанет в такой клетке");
    }

    @Test
    void order_Descent_FromEntranceDownwards() {
        List<MineColumn> columns = new ArrayList<>(MinePlan.columns(descentFloors(), descentAir()));
        Collections.reverse(columns);

        List<MineColumn> order = MinePlan.order(columns, new BlockCoord(0, 3, 0));

        for (int k = 0; k < STEPS; k++) {
            assertEquals(
                    new BlockCoord(k, -k, 0),
                    order.get(k).floor(),
                    "проходка идёт от входа вглубь: ступень " + k + " — " + k + "-я по счёту; порядок " + order);
        }
    }

    @Test
    void next_NothingOpen_ReturnsEntranceColumn() {
        List<MineColumn> order =
                MinePlan.order(MinePlan.columns(descentFloors(), descentAir()), new BlockCoord(0, 3, 0));

        assertEquals(
                Optional.of(order.get(0)),
                MinePlan.next(order, Set.of(), Set.of()),
                "первой копается колонка входа: в ней дух начинает спуск");
    }

    @Test
    void next_ClosedColumnInPath_SkipsToReachableOnly() {
        List<MineColumn> order =
                MinePlan.order(MinePlan.columns(descentFloors(), descentAir()), new BlockCoord(0, 3, 0));
        Set<MineColumn> open = new HashSet<>(List.of(order.get(0), order.get(1)));
        Set<MineColumn> closed = Set.of(order.get(2));

        assertEquals(
                Optional.empty(),
                MinePlan.next(order, open, closed),
                "за закрытой рудой колонкой ступень 3 недостижима пешком — следующей нет, нужен обход");
    }

    @Test
    void forward_DescentStep_ReturnsNextStepBelow() {
        List<MineColumn> order =
                MinePlan.order(MinePlan.columns(descentFloors(), descentAir()), new BlockCoord(0, 3, 0));

        assertEquals(
                Optional.of(order.get(3)),
                MinePlan.forward(order.get(1), order.get(2), order),
                "за ступенью 2 по ходу спуска — ступень 3: туда обход обязан вывести");
    }
}
