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

        assertEquals(
                1,
                span.bottomY(),
                "низ колонны — нижняя ступень, с которой дух начал подъём"
                        + " Иначе тест span_FeetOnRung_ReturnsWholeContiguousColumn перестанет ловить эту поломку. При смене span_FeetOnRung_ReturnsWholeContiguousColumn проверь это утверждение.");
        assertEquals(
                5,
                span.topY(),
                "верх колонны — последняя непрерывная ступень"
                        + " Иначе тест span_FeetOnRung_ReturnsWholeContiguousColumn перестанет ловить эту поломку. При смене span_FeetOnRung_ReturnsWholeContiguousColumn проверь это утверждение.");
    }

    @Test
    void span_TwoColumnsAtFeet_PicksColumnContainingFeet() {
        ClimbRoute.Span span = ClimbRoute.span(List.of(1, 2, 3, 8, 9), 8).orElseThrow();

        assertEquals(
                8,
                span.bottomY(),
                "дух у второй колонны обязан лезть по ней, а не по дальней: иначе он пойдёт в чужую сторону"
                        + " При смене span_TwoColumnsAtFeet_PicksColumnContainingFeet проверь это утверждение.");
        assertEquals(
                9,
                span.topY(),
                "верх второй колонны — её ступень"
                        + " Иначе тест span_TwoColumnsAtFeet_PicksColumnContainingFeet перестанет ловить эту поломку. При смене span_TwoColumnsAtFeet_PicksColumnContainingFeet проверь это утверждение.");
    }

    @Test
    void span_FeetBelowColumns_PicksNearestColumn() {
        ClimbRoute.Span span = ClimbRoute.span(List.of(3, 4, 5), 1).orElseThrow();

        assertEquals(
                3,
                span.bottomY(),
                "дух под колонной обязан лезть по ближайшей (единственной) колонне"
                        + " Иначе тест span_FeetBelowColumns_PicksNearestColumn перестанет ловить эту поломку. При смене span_FeetBelowColumns_PicksNearestColumn проверь это утверждение.");
    }

    @Test
    void span_UnorderedDuplicates_Normalized() {
        ClimbRoute.Span span = ClimbRoute.span(List.of(4, 3, 4, 2), 3).orElseThrow();

        assertEquals(
                2,
                span.bottomY(),
                "повторы и порядок высот не должны ломать границы колонны"
                        + " Иначе тест span_UnorderedDuplicates_Normalized перестанет ловить эту поломку. При смене span_UnorderedDuplicates_Normalized проверь это утверждение.");
        assertEquals(
                4,
                span.topY(),
                "повторы и порядок высот не должны ломать верх колонны"
                        + " Иначе тест span_UnorderedDuplicates_Normalized перестанет ловить эту поломку. При смене span_UnorderedDuplicates_Normalized проверь это утверждение.");
    }

    @Test
    void span_EmptyList_IsEmpty() {
        assertTrue(
                ClimbRoute.span(List.of(), 1).isEmpty(),
                "пустая колонна — это отсутствие лестницы: скрипт обязан получить пусто и отказать с причиной"
                        + " Иначе тест span_EmptyList_IsEmpty перестанет ловить эту поломку. При смене span_EmptyList_IsEmpty проверь это утверждение.");
    }

    @Test
    void targetY_Down_ReturnsBottomOtherwiseTop() {
        ClimbRoute.Span span = new ClimbRoute.Span(1, 6);

        assertEquals(
                6,
                span.targetY(false),
                "подъём идёт к верху колонны"
                        + " Иначе тест targetY_Down_ReturnsBottomOtherwiseTop перестанет ловить эту поломку. При смене targetY_Down_ReturnsBottomOtherwiseTop проверь это утверждение.");
        assertEquals(
                1,
                span.targetY(true),
                "спуск идёт к низу колонны"
                        + " Иначе тест targetY_Down_ReturnsBottomOtherwiseTop перестанет ловить эту поломку. При смене targetY_Down_ReturnsBottomOtherwiseTop проверь это утверждение.");
    }

    @Test
    void span_BottomAboveTop_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ClimbRoute.Span(6, 1),
                "низ выше верха — неверная колонна: такой span должен отвергаться, а не молча идти вверх ногами"
                        + " Иначе тест span_BottomAboveTop_IsRejected перестанет ловить эту поломку. При смене span_BottomAboveTop_IsRejected проверь это утверждение.");
    }
}
