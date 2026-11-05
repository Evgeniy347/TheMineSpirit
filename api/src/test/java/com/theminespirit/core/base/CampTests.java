package com.theminespirit.core.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.Point;
import org.junit.jupiter.api.Test;

class CampTests {
    @Test
    void camp_ValidInput_FieldsReadable() {
        Point center = new Point(10, 64, -3);

        Camp camp = new Camp(center, 16, 7, 100);

        assertEquals(
                center,
                camp.center(),
                "Центр лагеря обязан читаться: иначе лагерь некуда ставить и не с чем сравнивать точку отхода");
        assertEquals(
                16,
                camp.radius(),
                "Радиус лагеря обязан читаться: иначе спокойная зона неизвестна и оборона не знает, что крыть");
        assertEquals(7, camp.owner(), "Владелец лагеря обязан читаться: иначе семья не опознает свой лагерь похода");
        assertEquals(
                100, camp.setAt(), "Время постановки обязано читаться: иначе не отличить свежий лагерь от забытого");
    }

    @Test
    void camp_NullCenter_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Camp(null, 16, 0, 0),
                "Лагерь без центра обязан отвергаться: иначе точка отхода укажет в никуда");
    }

    @Test
    void camp_NonPositiveRadius_Throws() {
        Point center = new Point(0, 64, 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> new Camp(center, 0, 0, 0),
                "Нулевой радиус обязан отвергаться: иначе спокойная зона вырождается в точку");
        assertThrows(
                IllegalArgumentException.class,
                () -> new Camp(center, -2, 0, 0),
                "Отрицательный радиус обязан отвергаться: иначе contains пропустит всех или никого");
    }

    @Test
    void camp_NonFiniteRadius_Throws() {
        Point center = new Point(0, 64, 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> new Camp(center, Double.NaN, 0, 0),
                "NaN-радиус обязан отвергаться: иначе сравнение границы даст ложь на любой точке");
        assertThrows(
                IllegalArgumentException.class,
                () -> new Camp(center, Double.POSITIVE_INFINITY, 0, 0),
                "Бесконечный радиус обязан отвергаться: иначе лагерь накроет весь мир");
    }

    @Test
    void camp_NegativeOwnerOrTime_Throws() {
        Point center = new Point(0, 64, 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> new Camp(center, 16, -1, 0),
                "Отрицательный владелец обязан отвергаться: нет владельца — 0, а не −1");
        assertThrows(
                IllegalArgumentException.class,
                () -> new Camp(center, 16, 0, -1),
                "Отрицательное время постановки обязано отвергаться: тик установки не бывает до старта");
    }

    @Test
    void camp_EqualInstances_AreEqual() {
        Camp first = new Camp(new Point(1, 64, 2), 16, 5, 10);
        Camp second = new Camp(new Point(1, 64, 2), 16, 5, 10);

        assertEquals(
                first,
                second,
                "Равные лагеря обязаны совпадать: иначе сравнение лагеря после перезагрузки не сойдётся");
        assertEquals(
                first.hashCode(),
                second.hashCode(),
                "Хэш равных лагерей обязан совпадать: иначе лагерь потеряется в множествах");
    }

    @Test
    void contains_PointInside_ReturnsTrue() {
        Camp camp = new Camp(new Point(0, 64, 0), 16, 0, 0);

        assertTrue(
                camp.contains(new Point(10, 64, 0)),
                "Точка в 10 блоках при радиусе 16 обязана входить в лагерь: иначе спокойная зона уже заявленной");
    }

    @Test
    void contains_PointBeyondRadius_ReturnsFalse() {
        Camp camp = new Camp(new Point(0, 64, 0), 16, 0, 0);

        assertTrue(
                !camp.contains(new Point(17, 64, 0)),
                "Точка в 17 блоках при радиусе 16 обязана не входить в лагерь: иначе зона лагеря расползается");
    }

    @Test
    void contains_NullPoint_Throws() {
        Camp camp = new Camp(new Point(0, 64, 0), 16, 0, 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> camp.contains(null),
                "Проверка вхождения без точки обязана отвергаться: иначе null даст ложное спокойствие");
    }

    @Test
    void campTuning_DeclaredValues_MatchChecklistLiterals() {
        assertEquals(
                16,
                CampTuning.CAMP_RADIUS,
                "Радиус лагеря 16: как радиус обороны и лимит follow.radius, иначе духи разбредутся дальше охраны");
    }
}
