package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.movement.api.GazeAhead;
import org.junit.jupiter.api.Test;

class GazeAheadTests {
    @Test
    void steer_OnActivePathWithoutWork_IsTrue() {
        assertTrue(
                GazeAhead.steer(true, false),
                "Идущий дух смотрит по маршруту: иначе LookControl держит старую рабочую точку и через тик "
                        + "разворачивает корпус против хода — yaw мигает на 90–180° (окна 24–26). При смене "
                        + "правила проверь живой переход: дух идёт, взгляд по ближайшему узлу, а не назад");
    }

    @Test
    void steer_WhileWorking_IsFalse() {
        assertFalse(
                GazeAhead.steer(true, true),
                "При работе над блоком взгляд ведёт жест, а не маршрут: иначе дух смотрел бы мимо блока и рука "
                        + "била не туда. При смене правила проверь копку и установку блока: взгляд на клетку работы");
    }

    @Test
    void steer_WithoutPath_IsFalse() {
        assertFalse(
                GazeAhead.steer(false, false),
                "Без маршрута взгляд не перехватывают: иначе дух на месте терял бы осмысленный взгляд на "
                        + "собеседника или блок. При смене правила проверь стоящего духа без задания");
    }

    @Test
    void steer_WhileWorkingWithoutPath_IsFalse() {
        assertFalse(
                GazeAhead.steer(false, true),
                "Работа без маршрута — жест смотрит на клетку работы: иначе перехват взгляда на маршрут сломал бы "
                        + "точность удара по блоку. При смене правила проверь работу стоя: взгляд остаётся "
                        + "на блоке работы");
    }
}
