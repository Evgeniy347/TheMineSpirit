package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class MoveCostProperties {
    @Property
    void costOf_AnyMoveAndHeight_IsPositive(
            @ForAll MoveKind move,
            @ForAll @IntRange(min = 0, max = 32) int height) {
        assertTrue(MoveCost.costOf(move, height) > 0,
                "Любая стоимость движения обязана быть положительной: нулевая стоимость ломает упорядочивание "
                        + "целей — бесплатный путь всегда первый, даже если ведёт в стену");
    }
}
