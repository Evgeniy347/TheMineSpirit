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

        assertEquals(
                new FollowParams("Steve", 5),
                params,
                "follow обязан принять цель и радиус удержания: по ним скрипт решает, когда идти к игроку"
                        + " Иначе тест from_AllParamsPresent_ReturnsTargetAndRadius перестанет ловить эту поломку. При смене from_AllParamsPresent_ReturnsTargetAndRadius проверь это утверждение.");
    }

    @Test
    void radius_Missing_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> FollowParams.from(Map.of("target", "Steve")),
                "follow без радиуса обязан отказывать: валидатор плана подставляет значение по умолчанию "
                        + "из каталога до запуска, а без него скрипт не знает дистанцию"
                        + " Иначе тест radius_Missing_IsRejected перестанет ловить эту поломку. При смене radius_Missing_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("radius"),
                "отказ обязан называть недостающий радиус 'radius'. Фактическое: " + error.getMessage()
                        + " Иначе тест radius_Missing_IsRejected перестанет ловить эту поломку. При смене radius_Missing_IsRejected проверь это утверждение.");
    }

    @Test
    void target_Missing_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> FollowParams.from(Map.of("radius", 3)),
                "follow без цели обязан отказывать: без цели скрипт не знает, за кем следовать"
                        + " Иначе тест target_Missing_IsRejected перестанет ловить эту поломку. При смене target_Missing_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("target"),
                "отказ обязан называть недостающую цель 'target'. Фактическое: " + error.getMessage()
                        + " Иначе тест target_Missing_IsRejected перестанет ловить эту поломку. При смене target_Missing_IsRejected проверь это утверждение.");
    }

    @Test
    void target_Blank_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FollowParams.from(Map.of("target", "  ")),
                "follow с пустым именем цели обязан отказывать: пустую строку нельзя сопоставить с игроком"
                        + " Иначе тест target_Blank_IsRejected перестанет ловить эту поломку. При смене target_Blank_IsRejected проверь это утверждение.");
    }

    @Test
    void radius_Negative_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FollowParams.from(Map.of("target", "Steve", "radius", -1)),
                "follow с отрицательным радиусом обязан отказывать: дистанция меньше нуля бессмысленна"
                        + " Иначе тест radius_Negative_IsRejected перестанет ловить эту поломку. При смене radius_Negative_IsRejected проверь это утверждение.");
    }

    @Test
    void radius_WrongType_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FollowParams.from(Map.of("target", "Steve", "radius", "far")),
                "follow с нецелым радиусом обязан отказывать: строку нельзя тихо превратить в дистанцию"
                        + " Иначе тест radius_WrongType_IsRejected перестанет ловить эту поломку. При смене radius_WrongType_IsRejected проверь это утверждение.");
    }
}
