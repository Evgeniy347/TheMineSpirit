package com.theminespirit.core.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.husbandry.RingGround.Cell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RingBuildOrderTests {
    private static final Cell ANIMAL = new Cell(0, 0);

    @Test
    void order_LargeFlat_FirstBlockTouchesAnimalCell() {
        RingGround ground = RingGround.flat(-2, -2, 6, 6, 10);
        RingPlan.Ring ring = RingPlan.plan(ground, ANIMAL, RingPlan.Size.LARGE);

        List<BlockCoord> order = RingBuildOrder.order(ring, ANIMAL);

        BlockCoord first = order.get(0);
        int distance = Math.abs(first.x() - ANIMAL.x()) + Math.abs(first.z() - ANIMAL.z());
        assertEquals(1, distance,
                "Первый блок кольца обязан примыкать к клетке животного: стенку ставят вокруг зверя с ближней "
                        + "стороны, чтобы отрезать его от выхода; постановка с дальнего угла оставляет животному "
                        + "проход, пока дух идёт вокруг");
    }

    @Test
    void order_LargeFlat_EachColumnLowerBlockBeforeUpper() {
        RingGround ground = RingGround.flat(-2, -2, 6, 6, 10);
        RingPlan.Ring ring = RingPlan.plan(ground, ANIMAL, RingPlan.Size.LARGE);

        List<BlockCoord> order = RingBuildOrder.order(ring, ANIMAL);

        Map<Cell, List<Integer>> ysByCell = order.stream().collect(Collectors.groupingBy(
                block -> new Cell(block.x(), block.z()),
                Collectors.mapping(BlockCoord::y, Collectors.toList())));
        for (Map.Entry<Cell, List<Integer>> entry : ysByCell.entrySet()) {
            List<Integer> ys = entry.getValue();
            assertTrue(ys.get(0) < ys.get(ys.size() - 1),
                    "Клетка " + entry.getKey() + " обязана строиться снизу вверх: верхний блок без нижнего "
                            + "повисает без опоры, фактически " + ys);
        }
    }

    @Test
    void order_LargeFlat_CellsGoNearestAnimalFirst() {
        RingGround ground = RingGround.flat(-2, -2, 6, 6, 10);
        RingPlan.Ring ring = RingPlan.plan(ground, ANIMAL, RingPlan.Size.LARGE);

        List<BlockCoord> order = RingBuildOrder.order(ring, ANIMAL);

        int previous = -1;
        for (BlockCoord block : order) {
            int dx = block.x() - ANIMAL.x();
            int dz = block.z() - ANIMAL.z();
            int distance = dx * dx + dz * dz;
            assertTrue(distance >= previous,
                    "Блоки кольца обязаны идти от ближних к животному к дальним: иначе дух строит дальнюю "
                            + "стенку, а зверь выходит через ближнюю; блок " + block + " дальше предыдущего");
            previous = distance;
        }
    }

    @Test
    void order_SameInput_AlwaysSameSequence() {
        RingGround ground = RingGround.flat(-2, -2, 6, 6, 10);
        RingPlan.Ring ring = RingPlan.plan(ground, ANIMAL, RingPlan.Size.LARGE);

        List<BlockCoord> first = RingBuildOrder.order(ring, ANIMAL);
        List<BlockCoord> second = RingBuildOrder.order(ring, ANIMAL);

        assertEquals(first, second,
                "Порядок постройки обязан быть детерминированным: один и тот же план даёт одну и ту же "
                        + "последовательность, иначе дух мечется между стенками и не замыкает кольцо");
    }
}