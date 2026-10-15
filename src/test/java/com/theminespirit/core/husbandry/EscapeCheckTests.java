package com.theminespirit.core.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.husbandry.RingGround.Cell;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EscapeCheckTests {
    private static final Cell ANIMAL = new Cell(0, 0);

    @Test
    void stepUp_ProductionConstant_IsOneBlock() {
        assertEquals(1, EscapeCheck.STEP_UP_MAX,
                "Животное прыгает ровно на 1 блок: больший шаг считал бы ровную стенку в 2 блока проходимой");
    }

    @Test
    void findEscape_FullRingOnFlat_NoExit() {
        RingGround ground = RingGround.flat(-2, -2, 5, 5, 10);
        RingPlan.Ring ring = RingPlan.plan(ground, ANIMAL, RingPlan.Size.SMALL);

        Optional<EscapeCheck.WeakSpot> escape = EscapeCheck.findEscape(ground, ring.tops(), ring.interior());

        assertTrue(escape.isEmpty(),
                "Полное кольцо на ровной земле обязано держать: выход означает дыру в плане стенок");
    }

    @Test
    void findEscape_LoweredWallCell_ExitThroughThatCell() {
        RingGround ground = RingGround.flat(-2, -2, 5, 5, 10);
        RingPlan.Ring ring = RingPlan.plan(ground, ANIMAL, RingPlan.Size.SMALL);
        Map<Cell, Integer> tops = new HashMap<>(ring.tops());
        Cell lowered = new Cell(1, 0);
        tops.put(lowered, tops.get(lowered) - 1);

        Optional<EscapeCheck.WeakSpot> escape = EscapeCheck.findEscape(ground, tops, ring.interior());

        assertTrue(escape.isPresent(),
                "Стенка ниже на блок обязана выпускать: животное шагает вверх на 1 и спускается наружу");
        assertEquals(lowered, escape.orElseThrow().cell(),
                "Слабое место обязано указывать на заниженную стенку: иначе дух наращивает крепкую стенку, "
                        + "а дыра остаётся");
    }

    @Test
    void findEscape_MissingColumn_ExitThroughGap() {
        RingGround ground = RingGround.flat(-2, -2, 5, 5, 10);
        RingPlan.Ring ring = RingPlan.plan(ground, ANIMAL, RingPlan.Size.SMALL);
        Map<Cell, Integer> tops = new HashMap<>(ring.tops());
        Cell gap = new Cell(0, 1);
        tops.remove(gap);

        Optional<EscapeCheck.WeakSpot> escape = EscapeCheck.findEscape(ground, tops, ring.interior());

        assertTrue(escape.isPresent(),
                "Пропущенная стенка обязана выпускать: в gap животное выходит шагом без прыжка");
        assertEquals(gap, escape.orElseThrow().cell(),
                "Слабое место обязано указывать на пропуск: иначе дух чинит не ту стенку");
    }

    @Test
    void findEscape_MissingCornerBetweenHighWalls_NoDiagonalSqueeze() {
        RingGround ground = RingGround.flat(-2, -2, 5, 5, 10);
        RingPlan.Ring ring = RingPlan.plan(ground, ANIMAL, RingPlan.Size.SMALL);
        Map<Cell, Integer> tops = new HashMap<>(ring.tops());
        tops.remove(new Cell(1, 1));

        Optional<EscapeCheck.WeakSpot> escape = EscapeCheck.findEscape(ground, tops, ring.interior());

        assertTrue(escape.isEmpty(),
                "Пропуск угловой стенки между двумя высокими обязан держать: животное не проскакивает углом "
                        + "между двумя блоками");
    }

    @Test
    void findEscape_CornerGapBesideLowWall_ExitThroughCorner() {
        RingGround ground = RingGround.flat(-2, -2, 5, 5, 10);
        RingPlan.Ring ring = RingPlan.plan(ground, ANIMAL, RingPlan.Size.SMALL);
        Map<Cell, Integer> tops = new HashMap<>(ring.tops());
        tops.remove(new Cell(1, 1));
        tops.put(new Cell(1, 0), 11);

        Optional<EscapeCheck.WeakSpot> escape = EscapeCheck.findEscape(ground, tops, ring.interior());

        assertTrue(escape.isPresent(),
                "Угол с пропуском рядом с заниженной стенкой обязан выпускать по диагонали: животное проскакивает "
                        + "между блоками углом");
        assertEquals(new Cell(1, 0), escape.orElseThrow().cell(),
                "Слабое место обязано быть заниженной боковой стенкой: её наращивание закрывает угловой проход");
    }

    @Test
    void findEscape_LoweredCornerBetweenHighWalls_NoExit() {
        RingGround ground = RingGround.flat(-2, -2, 5, 5, 10);
        RingPlan.Ring ring = RingPlan.plan(ground, ANIMAL, RingPlan.Size.SMALL);
        Map<Cell, Integer> tops = new HashMap<>(ring.tops());
        tops.put(new Cell(1, 1), 11);

        Optional<EscapeCheck.WeakSpot> escape = EscapeCheck.findEscape(ground, tops, ring.interior());

        assertTrue(escape.isEmpty(),
                "Заниженный угол между двумя высокими стенками обязан держать: встать на него можно только "
                        + "диагональю между двумя блоками, а такой проход запрещён");
    }

    @Test
    void findEscape_SingleLowWallBesideHighOnes_NoDiagonalSqueeze() {
        RingGround ground = RingGround.flat(-2, -2, 5, 5, 10);
        RingPlan.Ring ring = RingPlan.plan(ground, ANIMAL, RingPlan.Size.SMALL);
        Map<Cell, Integer> tops = new HashMap<>(ring.tops());
        tops.put(new Cell(1, 0), 11);

        Optional<EscapeCheck.WeakSpot> escape = EscapeCheck.findEscape(ground, tops, ring.interior());

        assertTrue(escape.isPresent(),
                "Заниженная стенка выпускает напрямую, но обязана быть единственным проходом");
        assertEquals(new Cell(1, 0), escape.orElseThrow().cell(),
                "Одна заниженная стенка рядом с высокими обязана выпускать только через себя: диагональ мимо "
                        + "высокой стенки животное протиснуть не может");
    }

    @Test
    void findEscape_LargeRingOnStep_NoExit() {
        RingGround ground = RingGround.of(-2, -2, new int[][]{
                {5, 5, 5, 5, 5, 5},
                {5, 5, 5, 5, 5, 5},
                {5, 5, 6, 6, 5, 5},
                {5, 5, 6, 6, 5, 5},
                {5, 5, 5, 5, 5, 5},
                {5, 5, 5, 5, 5, 5}});
        RingPlan.Ring ring = RingPlan.plan(ground, ANIMAL, RingPlan.Size.LARGE);

        Optional<EscapeCheck.WeakSpot> escape = EscapeCheck.findEscape(ground, ring.tops(), ring.interior());

        assertTrue(escape.isEmpty(),
                "Полное большое кольцо на ступеньке обязано держать: животное не должно уходить с высокого пола");
    }
}
