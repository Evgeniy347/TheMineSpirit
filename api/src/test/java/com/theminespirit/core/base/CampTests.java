package com.theminespirit.core.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.api.Point;
import org.junit.jupiter.api.Test;

class CampTests {
    @Test
    void camp_ValidInput_FieldsReadable() {
        Point center = new Point(10, 64, -3);

        Camp camp = new Camp(center, 16, 7, 100);

        assertEquals(
                center,
                camp.center(),
                "Центр лагеря обязан читаться: иначе лагерь некуда ставить и не с чем сравнивать точку отхода"
                        + " При смене camp_ValidInput_FieldsReadable проверь это утверждение.");
        assertEquals(
                16,
                camp.radius(),
                "Радиус лагеря обязан читаться: иначе спокойная зона неизвестна и оборона не знает, что крыть"
                        + " При смене camp_ValidInput_FieldsReadable проверь это утверждение.");
        assertEquals(
                7,
                camp.owner(),
                "Владелец лагеря обязан читаться: иначе семья не опознает свой лагерь похода"
                        + " При смене camp_ValidInput_FieldsReadable проверь это утверждение.");
        assertEquals(
                100,
                camp.setAt(),
                "Время постановки обязано читаться: иначе не отличить свежий лагерь от забытого"
                        + " При смене camp_ValidInput_FieldsReadable проверь это утверждение.");
    }

    @Test
    void camp_NullCenter_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Camp(null, 16, 0, 0),
                "Лагерь без центра обязан отвергаться: иначе точка отхода укажет в никуда"
                        + " При смене camp_NullCenter_Throws проверь это утверждение.");
    }

    @Test
    void camp_NonPositiveRadius_Throws() {
        Point center = new Point(0, 64, 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> new Camp(center, 0, 0, 0),
                "Нулевой радиус обязан отвергаться: иначе спокойная зона вырождается в точку"
                        + " При смене camp_NonPositiveRadius_Throws проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new Camp(center, -2, 0, 0),
                "Отрицательный радиус обязан отвергаться: иначе contains пропустит всех или никого"
                        + " При смене camp_NonPositiveRadius_Throws проверь это утверждение.");
    }

    @Test
    void camp_NonFiniteRadius_Throws() {
        Point center = new Point(0, 64, 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> new Camp(center, Double.NaN, 0, 0),
                "NaN-радиус обязан отвергаться: иначе сравнение границы даст ложь на любой точке"
                        + " При смене camp_NonFiniteRadius_Throws проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new Camp(center, Double.POSITIVE_INFINITY, 0, 0),
                "Бесконечный радиус обязан отвергаться: иначе лагерь накроет весь мир"
                        + " При смене camp_NonFiniteRadius_Throws проверь это утверждение.");
    }

    @Test
    void camp_NegativeOwnerOrTime_Throws() {
        Point center = new Point(0, 64, 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> new Camp(center, 16, -1, 0),
                "Отрицательный владелец обязан отвергаться: нет владельца — 0, а не −1"
                        + " Иначе тест camp_NegativeOwnerOrTime_Throws перестанет ловить эту поломку. При смене camp_NegativeOwnerOrTime_Throws проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new Camp(center, 16, 0, -1),
                "Отрицательное время постановки обязано отвергаться: тик установки не бывает до старта"
                        + " Иначе тест camp_NegativeOwnerOrTime_Throws перестанет ловить эту поломку. При смене camp_NegativeOwnerOrTime_Throws проверь это утверждение.");
    }

    @Test
    void camp_EqualInstances_AreEqual() {
        Camp first = new Camp(new Point(1, 64, 2), 16, 5, 10);
        Camp second = new Camp(new Point(1, 64, 2), 16, 5, 10);

        assertEquals(
                first,
                second,
                "Равные лагеря обязаны совпадать: иначе сравнение лагеря после перезагрузки не сойдётся"
                        + " При смене camp_EqualInstances_AreEqual проверь это утверждение.");
        assertEquals(
                first.hashCode(),
                second.hashCode(),
                "Хэш равных лагерей обязан совпадать: иначе лагерь потеряется в множествах"
                        + " При смене camp_EqualInstances_AreEqual проверь это утверждение.");
    }

    @Test
    void contains_PointInside_ReturnsTrue() {
        Camp camp = new Camp(new Point(0, 64, 0), 16, 0, 0);

        assertTrue(
                camp.contains(new Point(10, 64, 0)),
                "Точка в 10 блоках при радиусе 16 обязана входить в лагерь: иначе спокойная зона уже заявленной"
                        + " При смене contains_PointInside_ReturnsTrue проверь это утверждение.");
    }

    @Test
    void contains_PointBeyondRadius_ReturnsFalse() {
        Camp camp = new Camp(new Point(0, 64, 0), 16, 0, 0);

        assertTrue(
                !camp.contains(new Point(17, 64, 0)),
                "Точка в 17 блоках при радиусе 16 обязана не входить в лагерь: иначе зона лагеря расползается"
                        + " При смене contains_PointBeyondRadius_ReturnsFalse проверь это утверждение.");
    }

    @Test
    void contains_NullPoint_Throws() {
        Camp camp = new Camp(new Point(0, 64, 0), 16, 0, 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> camp.contains(null),
                "Проверка вхождения без точки обязана отвергаться: иначе null даст ложное спокойствие"
                        + " При смене contains_NullPoint_Throws проверь это утверждение.");
    }

    @Test
    void campTuning_DeclaredValues_MatchChecklistLiterals() {
        assertEquals(
                16,
                CampTuning.CAMP_RADIUS,
                "Радиус лагеря 16: как радиус обороны и лимит follow.radius, иначе духи разбредутся дальше охраны"
                        + " При смене campTuning_DeclaredValues_MatchChecklistLiterals проверь это утверждение.");
    }
}
