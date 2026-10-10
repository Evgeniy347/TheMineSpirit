package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class FollowParamsTests {
    @Test
    void from_AllParamsPresent_ReturnsTargetAndRadius() {
        FollowParams params = FollowParams.from(Map.of("target", "Steve", "radius", 5));

        assertEquals(new FollowParams("Steve", 5), params,
                "follow обязан принять цель и радиус удержания: по ним скрипт решает, когда идти к игроку");
    }

    @Test
    void radius_Missing_DefaultsToThree() {
        FollowParams params = FollowParams.from(Map.of("target", "Steve"));

        assertEquals(FollowParams.DEFAULT_RADIUS, params.radius(),
                "радиус follow необязателен и обязан иметь значение по умолчанию: без него скрипт не знает дистанцию");
    }

    @Test
    void target_Missing_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> FollowParams.from(Map.of("radius", 3)),
                "follow без цели обязан отказывать: без цели скрипт не знает, за кем следовать");

        assertTrue(error.getMessage().contains("target"),
                "отказ обязан называть недостающую цель 'target'. Фактическое: " + error.getMessage());
    }

    @Test
    void target_Blank_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> FollowParams.from(Map.of("target", "  ")),
                "follow с пустым именем цели обязан отказывать: пустую строку нельзя сопоставить с игроком");
    }

    @Test
    void radius_Negative_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> FollowParams.from(Map.of("target", "Steve", "radius", -1)),
                "follow с отрицательным радиусом обязан отказывать: дистанция меньше нуля бессмысленна");
    }

    @Test
    void radius_WrongType_IsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> FollowParams.from(Map.of("target", "Steve", "radius", "far")),
                "follow с нецелым радиусом обязан отказывать: строку нельзя тихо превратить в дистанцию");
    }
}