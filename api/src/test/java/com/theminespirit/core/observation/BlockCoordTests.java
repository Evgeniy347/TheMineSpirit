package com.theminespirit.core.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BlockCoordTests {
    @Test
    void plus_RelativeSupply_AddsOrigin() {
        assertEquals(
                new BlockCoord(-174, 68, -509),
                new BlockCoord(4, 0, 6).plus(new BlockCoord(-178, 68, -515)),
                "Стройка печатает стоянку снабжения в координатах мира, а не смещением от участка: иначе "
                        + "«сундук у 4,0,6» не найти в мире");
    }

    @Test
    void plus_NegativeOffsetAndOrigin_KeepsSigns() {
        assertEquals(
                new BlockCoord(-10, 60, -3),
                new BlockCoord(-3, -2, 4).plus(new BlockCoord(-7, 62, -7)),
                "Отрицательные смещение и участок складываются со знаком: иначе лог уводит на другой край");
    }
}
