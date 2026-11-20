package com.theminespirit.core.movement.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GazeTurnTests {
    @Test
    void forWork_WhileWorking_TurnsSlowly() {
        assertEquals(
                GazeTurn.WORK_DEGREES_PER_TICK,
                GazeTurn.forWork(true),
                "При работе с блоком корпус доворачивается не быстрее 30 градусов за тик: иначе LookControl "
                        + "разворачивает тело на 180 градусов к блоку, а MoveControl ведёт его по пути в "
                        + "противоположную сторону, и дух крутится на месте (спины 310–1800 градусов, "
                        + "окна 11–14). При смене скорости in SpiritGesture.face проверь живой patrol: "
                        + "тело идёт по маршруту, голова смотрит на блок");
    }

    @Test
    void forWork_NotWorking_KeepsFastTurn() {
        assertEquals(
                GazeTurn.IDLE_DEGREES_PER_TICK,
                GazeTurn.forWork(false),
                "Без работы запас поворота остаётся прежним: иначе дух не успеет обернуться к цели "
                        + "взаимодействия и пропустит короткое окно. При смене GazeTurn проверь "
                        + "CombatActuator и быстрый разворот к угрозе: там нужны быстрые 30–180 градусов");
    }
}
