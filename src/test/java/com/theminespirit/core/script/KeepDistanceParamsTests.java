package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class KeepDistanceParamsTests {
    @Test
    void from_AllParamsPresent_ReturnsParams() {
        KeepDistanceParams params = KeepDistanceParams.from(Map.of("target", "creeper", "distance", 5));

        assertEquals(
                new KeepDistanceParams("creeper", 5),
                params,
                "keep_distance обязан сохранить цель и дистанцию: по ним скрипт решает, подходить или отходить");
    }

    @Test
    void from_MissingTarget_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> KeepDistanceParams.from(Map.of("distance", 5)),
                "keep_distance без цели обязан отказывать: без цели скрипт не знает, от кого держать дистанцию");

        assertTrue(
                error.getMessage().contains("target"),
                "Отказ обязан называть недостающую цель 'target'. Фактическое: " + error.getMessage());
    }

    @Test
    void from_BlankTarget_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> KeepDistanceParams.from(Map.of("target", "  ", "distance", 5)),
                "keep_distance с пустой целью обязан отказывать: пустую строку нельзя сопоставить с сущностью");
    }

    @Test
    void from_DistanceOutOfRange_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> KeepDistanceParams.from(Map.of("target", "creeper", "distance", 17)),
                "Дистанция больше 16 обязана отвергаться: дальше дух уходит из тикающей зоны за целью");
    }

    @Test
    void from_MissingDistance_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> KeepDistanceParams.from(Map.of("target", "creeper")),
                "keep_distance без дистанции обязан отказывать: валидатор плана подставляет значение по умолчанию "
                        + "из каталога до запуска, а без него скрипт не знает, где встать");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> KeepDistanceParams.from(null),
                "KeepDistanceParams.from(null) обязан отказывать: без карты параметров маппинг только читает состояние мира");
    }
}
