package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Box3Tests {
    private static final Box3 BODY = new Box3(0.2, 0.0, 0.2, 0.8, 1.4, 0.8);

    @Test
    void intersects_FencePostInBody_True() {
        Box3 post = new Box3(0.375, 0.0, 0.375, 0.625, 1.5, 0.625);
        assertTrue(BODY.intersects(post), "Дух внутри столба забора — замурован: шаг ходьбы не начать, нужен dig_out");
    }

    @Test
    void intersects_SlabUnderFeet_False() {
        Box3 slab = new Box3(0.0, 0.0, 0.0, 1.0, 0.5, 1.0);
        Box3 onSlab = new Box3(0.2, 0.5, 0.2, 0.8, 1.9, 0.8);
        assertFalse(
                onSlab.intersects(slab), "Дух стоит НА полублоке, а не внутри него: это обычная стоянка, не замурован");
    }
}
