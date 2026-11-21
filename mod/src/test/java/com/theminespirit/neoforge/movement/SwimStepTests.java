package com.theminespirit.neoforge.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SwimStepTests {
    @Test
    void far_SmallLag_IsAccepted() {
        assertFalse(
                SwimStep.far(1.8, 0.4),
                "лаг меньше двух клеток — дух ещё идёт к шагу, а не телепортировался"
                        + " Иначе тест far_SmallLag_IsAccepted перестанет ловить эту поломку. При смене far_SmallLag_IsAccepted проверь это утверждение.");
        assertFalse(
                SwimStep.far(2.5, 2.5),
                "сдвиг на две клетки по диагонали допустим после падения"
                        + " Иначе тест far_SmallLag_IsAccepted перестанет ловить эту поломку. При смене far_SmallLag_IsAccepted проверь это утверждение.");
    }

    @Test
    void far_Teleport_IsRejected() {
        assertTrue(
                SwimStep.far(2.7, 0),
                "шаг дальше двух клеток — уже не соседняя клетка"
                        + " Иначе тест far_Teleport_IsRejected перестанет ловить эту поломку. При смене far_Teleport_IsRejected проверь это утверждение.");
        assertTrue(
                SwimStep.far(0, 3.0),
                "вертикальный обрыв не маскирует скачок по Z"
                        + " Иначе тест far_Teleport_IsRejected перестанет ловить эту поломку. При смене far_Teleport_IsRejected проверь это утверждение.");
    }
}
