package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.movement.api.StepFall;
import org.junit.jupiter.api.Test;

class StepFallTests {
    @Test
    void fell_BelowStepInAir_IsTrue() {
        assertTrue(
                StepFall.fell(68, 71, false),
                "Дух сорвался ниже ступени и уже не стоит на земле: шаг провален, поворачивать сразу, а не "
                        + "ждать 60 тиков и повторять тот же прыжок");
    }

    @Test
    void fell_OnSameLevelInAir_IsFalse() {
        assertFalse(
                StepFall.fell(70, 71, false),
                "Обычный прыжок на ступень не считается падением: дух ещё в воздухе над своей клеткой");
    }

    @Test
    void fell_BelowStepOnGround_IsFalse() {
        assertFalse(StepFall.fell(68, 71, true), "Стоящий на земле дух готов к следующей ступени: это не срыв");
    }
}
