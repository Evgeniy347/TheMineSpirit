package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FenceGateRuleTests {
    @Test
    void gates_Side23_OneGateInMiddle() {
        assertEquals(
                List.of(11),
                FenceGateRule.gates(23),
                "На стороне 23 калитка одна и посередине: отрезки до углов по 11, иначе калитка к углу ближе 10");
    }

    @Test
    void gates_ShortSides_HaveNoGate() {
        assertEquals(List.of(), FenceGateRule.gates(20), "Сторона 20 короче двух отрезков по 10: калитки нет");
        assertEquals(List.of(), FenceGateRule.gates(8), "Сторона 8 — не периметр поселения: калитки нет");
    }

    @Test
    void gates_Side21_OneGateEvenly() {
        assertEquals(
                List.of(10),
                FenceGateRule.gates(21),
                "На стороне 21 калитка посередине даёт два отрезка по 10 — нижняя граница правила");
    }

    @Test
    void gates_Side42_TwoEvenlySpaced() {
        assertEquals(
                List.of(14, 28),
                FenceGateRule.gates(42),
                "На стороне 42 двух калиток хватает: отрезки 14, 14 и 13 — каждый в границах 10–20");
    }

    @Test
    void gates_EverySideLength_SegmentsWithinTenAndTwenty() {
        List<String> problems = new ArrayList<>();
        for (int length = 21; length <= 400; length++) {
            List<Integer> points = new ArrayList<>();
            points.add(0);
            points.addAll(FenceGateRule.gates(length));
            points.add(length - 1);
            for (int index = 1; index < points.size(); index++) {
                int span = points.get(index) - points.get(index - 1);
                if (span < FenceGateRule.MIN_SPAN || span > FenceGateRule.MAX_SPAN) {
                    problems.add("сторона " + length + ": отрезок " + span);
                }
            }
        }

        assertEquals(
                List.of(),
                problems,
                "Любая сторона от 21: каждый отрезок между углом и калиткой 10–20 блоков, иначе калитки "
                        + "либо слишком редкие (дыра в обороне), либо слишком частые (лишние проходы)");
    }

    @Test
    void gates_EverySideLength_SpacingDiffersByAtMostOne() {
        List<String> problems = new ArrayList<>();
        for (int length = 21; length <= 400; length++) {
            List<Integer> points = new ArrayList<>();
            points.add(0);
            points.addAll(FenceGateRule.gates(length));
            points.add(length - 1);
            int min = Integer.MAX_VALUE;
            int max = 0;
            for (int index = 1; index < points.size(); index++) {
                int span = points.get(index) - points.get(index - 1);
                min = Math.min(min, span);
                max = Math.max(max, span);
            }
            if (max - min > 1) {
                problems.add("сторона " + length + ": " + min + ".." + max);
            }
        }

        assertEquals(
                List.of(),
                problems,
                "Калитки на стороне стоят равномерно: разница соседних отрезков не больше блока, иначе калитка "
                        + "сбивается к одному краю");
    }

    @Test
    void gates_SideShorterThanOne_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FenceGateRule.gates(0),
                "Сторона 0 — ошибка раскладки: молчаливое пустое правило спрятало бы сломанный размер ограды");
        assertTrue(
                FenceGateRule.gates(1).isEmpty(),
                "Сторона 1 — одна клетка без калитки: на ней негде развернуть отрезок 10");
    }
}
