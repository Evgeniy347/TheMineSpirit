package com.theminespirit.core.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteTrailTests {
    @Test
    void last_MoreStepsThanKeep_ReturnsNewestInOrder() {
        RouteTrail trail = new RouteTrail(5);
        for (int x = 0; x < 7; x++) {
            trail.add(new RouteStep(RouteStep.Action.WALK, new BlockCoord(x, 0, 0)));
        }

        assertEquals(
                List.of(cell(2), cell(3), cell(4), cell(5), cell(6)),
                trail.last(),
                "История держит последние 5 шагов в порядке исполнения: по строке смерти видно, откуда дух шёл");
    }

    @Test
    void keep_NotPositive_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RouteTrail(0),
                "Нулевая длина истории бессмысленна: строка смерти осталась бы без шагов маршрута");
    }

    private static RouteStep cell(int x) {
        return new RouteStep(RouteStep.Action.WALK, new BlockCoord(x, 0, 0));
    }
}
