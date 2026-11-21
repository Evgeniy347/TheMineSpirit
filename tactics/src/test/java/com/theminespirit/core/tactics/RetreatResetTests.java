package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RetreatResetTests {
    @Test
    void needed_NoState_IsFalse() {
        assertFalse(
                RetreatReset.needed(false, false),
                "Сброс отступления без состояния ничего не делает: иначе ежесекундный вызов сброса отменяет "
                        + "чужой подход возврата — путь к якорю сбрасывается и строится заново каждый тик "
                        + "(окно 36: spirit-1, 561 перестройка к -156,70,-489, «прошлый путь нет»). При смене "
                        + "TacticsReturn.walk проверь живой возврат: путь строится один раз, а не каждый тик");
    }

    @Test
    void needed_CurrentPlan_IsTrue() {
        assertTrue(
                RetreatReset.needed(true, false),
                "Сброс при активном плане отступления обязателен: иначе дух продолжит уходить по старому "
                        + "плану после смены решения и не вернётся к работе. При смене правила проверь смену "
                        + "плана отступления: старый маршрут отменяется");
    }

    @Test
    void needed_LastTarget_IsTrue() {
        assertTrue(
                RetreatReset.needed(false, true),
                "Сброс при оставшейся цели отступления обязателен: иначе подход с прошлой целью останется "
                        + "активным и дух пойдёт не туда. При смене правила проверь конец отступления: "
                        + "оставшаяся цель снимается");
    }

    @Test
    void needed_BothPresent_IsTrue() {
        assertTrue(
                RetreatReset.needed(true, true),
                "Сброс при плане и цели вместе обязателен: иначе оба признака останутся в памяти и сброс "
                        + "не освободит подход. При смене правила проверь полное состояние отступления: "
                        + "оба признака снимаются одним сбросом");
    }
}
