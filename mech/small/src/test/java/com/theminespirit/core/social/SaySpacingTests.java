package com.theminespirit.core.social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SaySpacingTests {
    @Test
    void due_NeverSaid_ReturnsTrue() {
        assertTrue(
                SaySpacing.due(SaySpacing.NEVER, 100, 20),
                "Первое сообщение обязано разрешаться сразу: анти-спам не имеет права глушить первую речь, "
                        + "иначе дух молчит впервые");
    }

    @ParameterizedTest(name = "прошло {0} тиков из 20 — молчать")
    @ValueSource(ints = {0, 1, 19})
    void due_InsideInterval_ReturnsFalse(int passed) {
        assertFalse(
                SaySpacing.due(100, 100 + passed, 20),
                "Внутри интервала сообщение обязано подождать: без паузы дух застрял бы в ленте реплик "
                        + "и забил бы чат");
    }

    @Test
    void due_ExactIntervalElapsed_ReturnsTrue() {
        assertTrue(
                SaySpacing.due(100, 120, 20),
                "Ровно по истечении интервала сообщение обязано разрешаться: граница включительная, "
                        + "иначе анти-спам стал бы жёстче заданного");
    }

    @Test
    void due_TimeMovedBackwards_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SaySpacing.due(100, 99, 20),
                "Время назад обязано отказывать: тики не отматываются, а молчаливая ложь разрешила бы спам "
                        + "после отката мира");

        assertTrue(
                error.getMessage().contains("назад"),
                "Отказ обязан называть проблему со временем. Фактическое: " + error.getMessage());
    }

    @Test
    void due_NegativeInterval_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SaySpacing.due(100, 200, -1),
                "Отрицательный интервал обязан отказывать: он разрешил бы говорить чаще, чем задано, "
                        + "и превратил бы анти-спам в декорацию");

        assertTrue(
                error.getMessage().contains("-1"),
                "Отказ обязан называть отклонённый интервал. Фактическое: " + error.getMessage());
    }

    @Test
    void due_ZeroInterval_ReturnsTrue() {
        assertTrue(
                SaySpacing.due(100, 100, 0),
                "Нулевой интервал обязан пропускать сообщение сразу: анти-спам выключен — говорить можно");
        assertEquals(
                true,
                SaySpacing.due(100, 101, 0),
                "Нулевой интервал обязан пропускать сообщение в любом будущем тике: пауз не предписано");
    }
}
