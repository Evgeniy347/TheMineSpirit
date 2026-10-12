package com.theminespirit.core.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.observation.ScanRadius;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class PerceptionQueryProperties {
    @Property
    void radius_AnyValueInRange_QueryKeepsItUnchanged(@ForAll PerceptionType type,
            @ForAll @IntRange(min = ScanRadius.MIN, max = ScanRadius.MAX) int radius) {
        PerceptionQuery query = new PerceptionQuery(type, IdFilter.matchAll(), new ScanRadius(radius));

        assertEquals(radius, query.radius().value(), "радиус запроса обязан остаться тем, что задан");
        assertEquals(type, query.type(), "тип запроса обязан остаться тем, что задан");
    }

    @Property
    void radius_OutsideRange_QueryCannotBeBuilt(@ForAll PerceptionType type,
            @ForAll @IntRange(min = -1000, max = 1000) int radius) {
        Assume.that(radius < ScanRadius.MIN || radius > ScanRadius.MAX);

        assertThrows(IllegalArgumentException.class,
                () -> new PerceptionQuery(type, IdFilter.matchAll(), new ScanRadius(radius)),
                "запрос с радиусом " + radius + " вне границ ScanRadius обязан отклоняться при построении");
    }
}
