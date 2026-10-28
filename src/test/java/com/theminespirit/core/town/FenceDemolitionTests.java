package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FenceDemolitionTests {
    private static final FenceLine RING = new FenceLine(
            List.of(new ColumnMask.Cell(0, 0), new ColumnMask.Cell(1, 0), new ColumnMask.Cell(2, 0)), List.of());
    private static final ColumnMask STRIP = FenceStrip.of(RING, 1);

    @Test
    void allowed_FenceAndGateInsideStrip_True() {
        assertTrue(
                FenceDemolition.allowed("minecraft:oak_fence", STRIP, 1, 0),
                "Забор в полосе ограды сносится: старую ограду ставят заново на y+1 (решение владельца 28.10)");
        assertTrue(
                FenceDemolition.allowed("minecraft:oak_fence_gate", STRIP, 1, 1),
                "Калитка в полосе ограды сносится вместе с забором: это та же старая ограда");
    }

    @Test
    void allowed_FenceOutsideStrip_False() {
        assertFalse(
                FenceDemolition.allowed("minecraft:oak_fence", STRIP, 1, 2),
                "Забор вне полосы ограды защищён как прежде: это постройка игрока (protection.json → deny)");
    }

    @Test
    void allowed_NotFenceInsideStrip_False() {
        assertFalse(
                FenceDemolition.allowed("minecraft:oak_door", STRIP, 1, 0),
                "Дверь в полосе ограды не трогается: исключение только для заборов и калиток");
        assertFalse(
                FenceDemolition.allowed("minecraft:oak_sign", STRIP, 1, 0),
                "Табличка в полосе ограды не трогается: исключение только для заборов и калиток");
    }
}
