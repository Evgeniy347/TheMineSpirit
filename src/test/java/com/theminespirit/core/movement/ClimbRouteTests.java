package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ClimbRouteTests {
    @Test
    void span_FeetOnRung_ReturnsWholeContiguousColumn() {
        ClimbRoute.Span span = ClimbRoute.span(List.of(1, 2, 3, 4, 5), 1).orElseThrow();

        assertEquals(1, span.bottomY(), "низ колонны — нижняя ступень, с которой дух начал подъём");
        assertEquals(5, span.topY(), "верх колонны — последняя непрерывная ступень");
    }

    @Test
    void span_TwoColumnsAtFeet_PicksColumnContainingFeet() {
        ClimbRoute.Span span = ClimbRoute.span(List.of(1, 2, 3, 8, 9), 8).orElseThrow();

        assertEquals(
                8,
                span.bottomY(),
                "дух у второй колонны обязан лезть по ней, а не по дальней: иначе он пойдёт в чужую сторону");
        assertEquals(9, span.topY(), "верх второй колонны — её ступень");
    }

    @Test
    void span_FeetBelowColumns_PicksNearestColumn() {
        ClimbRoute.Span span = ClimbRoute.span(List.of(3, 4, 5), 1).orElseThrow();

        assertEquals(3, span.bottomY(), "дух под колонной обязан лезть по ближайшей (единственной) колонне");
    }

    @Test
    void span_UnorderedDuplicates_Normalized() {
        ClimbRoute.Span span = ClimbRoute.span(List.of(4, 3, 4, 2), 3).orElseThrow();

        assertEquals(2, span.bottomY(), "повторы и порядок высот не должны ломать границы колонны");
        assertEquals(4, span.topY(), "повторы и порядок высот не должны ломать верх колонны");
    }

    @Test
    void span_EmptyList_IsEmpty() {
        assertTrue(
                ClimbRoute.span(List.of(), 1).isEmpty(),
                "пустая колонна — это отсутствие лестницы: скрипт обязан получить пусто и отказать с причиной");
    }

    @Test
    void targetY_Down_ReturnsBottomOtherwiseTop() {
        ClimbRoute.Span span = new ClimbRoute.Span(1, 6);

        assertEquals(6, span.targetY(false), "подъём идёт к верху колонны");
        assertEquals(1, span.targetY(true), "спуск идёт к низу колонны");
    }

    @Test
    void span_BottomAboveTop_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ClimbRoute.Span(6, 1),
                "низ выше верха — неверная колонна: такой span должен отвергаться, а не молча идти вверх ногами");
    }
}
