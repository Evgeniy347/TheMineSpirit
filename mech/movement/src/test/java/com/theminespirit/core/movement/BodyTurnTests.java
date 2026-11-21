package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.movement.api.BodyTurn;
import org.junit.jupiter.api.Test;

class BodyTurnTests {
    @Test
    void limit_SmallStep_IsUnchanged() {
        assertEquals(
                100.0F,
                BodyTurn.limit(90.0F, 100.0F, 40.0F),
                "Малый доворот тела не ограничивают: иначе дух не успевал бы смотреть на работу и на ход, а "
                        + "каждая команда запаздывала бы на тики. При смене BodyTurn.limit проверь работу у блока: "
                        + "тело доворачивается точно на цель");
    }

    @Test
    void limit_LargeStep_IsClamped() {
        assertEquals(
                50.0F,
                BodyTurn.limit(90.0F, 320.0F, 40.0F),
                "Большой разворот режут до 40° за тик: иначе на уступах шахты тело прыгает на 90–130° за тик "
                        + "(окно 27, спуск spirit-3 yaw 91→128→182), и дух выглядит дёргающимся. При смене шага "
                        + "проверь живой спуск по уступам: тело разворачивается плавно, шаг не теряется");
    }

    @Test
    void limit_WrapAroundZero_TurnsShortWay() {
        assertEquals(
                10.0F,
                BodyTurn.limit(350.0F, 10.0F, 40.0F),
                "Через ноль доворот идёт коротким путём: иначе дух вместо 20° разворачивался бы на 340° и "
                        + "описывал круг на месте. При смене BodyTurn.limit проверь поворот через север: без круга");
    }

    @Test
    void limit_WrapAroundZeroLarge_IsClamped() {
        assertEquals(
                -50.0F,
                BodyTurn.limit(350.0F, 270.0F, 40.0F),
                "Обратный доворот через ноль тоже режут: иначе внезапный разворот на 80° перескакивал бы "
                        + "ограничение из-за переполнения угла. При смене BodyTurn.limit проверь разворот "
                        + "с севера на запад: не больше сорока градусов за тик");
    }
}
