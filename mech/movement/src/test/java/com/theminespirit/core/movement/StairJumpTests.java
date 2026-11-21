package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StairJumpTests {
    @Test
    void needed_StepUpAgainstWall_Jumps() {
        assertTrue(
                StairJump.needed(1, true, true),
                "Ступень выше на блок при упоре в стену требует явного прыжка: иначе ванильный MoveControl крутит "
                        + "тело на 90° каждый тик и дух вертится на месте 2 секунды (окно 62, spirit-4 dig_out: "
                        + "1320° за 97 тиков, позы по кругу радиусом 0.3). При смене правила проверь подъём по "
                        + "открытой лестнице без стены: он идёт шагом");
    }

    @Test
    void needed_StepUpWithoutWall_NoJump() {
        assertFalse(
                StairJump.needed(1, true, false),
                "Ступень выше без упора берётся обычным шагом: иначе лишний прыжок на каждом шаге превращает спуск в "
                        + "чехарду и сбивает аренду клеток. При смене правила проверь подъём по открытой "
                        + "лестнице: прыжок не нужен");
    }

    @Test
    void needed_FlatGround_NoJump() {
        assertFalse(
                StairJump.needed(0, true, true),
                "Ровный шаг в стену прыжком не лечится: иначе дух прыгает в стену и всё равно не проходит вперёд, а "
                        + "только теряет управление над шагом. При смене правила проверь ровный шаг в стену: "
                        + "прыжка нет");
    }

    @Test
    void needed_InAir_NoJump() {
        assertFalse(
                StairJump.needed(1, false, true),
                "В воздухе прыжок не добавить: иначе повторный вызов обнулит вертикальную скорость падения и дух "
                        + "зависнет над ступенью. При смене правила проверь падение на ступень: прыжка нет");
    }
}
