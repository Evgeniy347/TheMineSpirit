package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class StepOverParamsTests {
    @Test
    void from_StepPresent_ReturnsStep() {
        StepOverParams params = StepOverParams.from(Map.of("dx", 1, "dz", 0));

        assertEquals(
                new StepOverParams(1, 0),
                params,
                "step_over обязан сохранить шаг: по нему скрипт проверяет соседнюю клетку и шагает");
    }

    @Test
    void from_MissingDz_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StepOverParams.from(Map.of("dx", 1)),
                "step_over без dz обязан отказывать: без второй координаты шаг не задан");
    }

    @Test
    void from_ZeroStep_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StepOverParams.from(Map.of("dx", 0, "dz", 0)),
                "Шаг (0, 0) обязан отвергаться: стояние на месте — не шаг, тактика отхода так не работает");
    }

    @Test
    void from_FarStep_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StepOverParams.from(Map.of("dx", 2, "dz", 0)),
                "Шаг на 2 клетки обязан отвергаться: step_over — один шаг на соседнюю клетку, дальний прыжок "
                        + "проверяется иначе");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> StepOverParams.from(null),
                "StepOverParams.from(null) обязан отказывать: без карты параметров маппинг только читает состояние мира");
    }
}
