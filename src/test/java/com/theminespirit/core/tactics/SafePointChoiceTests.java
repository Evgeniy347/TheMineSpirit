package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SafePointChoiceTests {
    private static final Point SELF = new Point(0, 64, 0);
    private static final Point HOME = new Point(10, 64, 0);
    private static final Point CAMP = new Point(5, 64, 0);

    @Test
    void nearest_OnlyHomePresent_ReturnsHome() {
        Optional<Point> choice = SafePointChoice.nearest(SELF, Optional.of(HOME), Optional.empty());

        assertEquals(Optional.of(HOME), choice,
                "Без лагеря отход обязан идти домой: единственный известный безопасный адрес — дом семьи");
    }

    @Test
    void nearest_OnlyCampPresent_ReturnsCamp() {
        Optional<Point> choice = SafePointChoice.nearest(SELF, Optional.empty(), Optional.of(CAMP));

        assertEquals(Optional.of(CAMP), choice,
                "Без дома отход обязан идти в лагерь: единственный известный безопасный адрес — лагерь похода");
    }

    @Test
    void nearest_BothPointsPresent_ReturnsCloserCamp() {
        Optional<Point> choice = SafePointChoice.nearest(SELF, Optional.of(HOME), Optional.of(CAMP));

        assertEquals(Optional.of(CAMP), choice,
                "С двумя точками отход обязан идти к ближайшей: дальний адрес растянет путь и дольше держит "
                        + "духа под угрозой");
    }

    @Test
    void nearest_DistanceTie_ReturnsHome() {
        Point campAway = new Point(-10, 64, 0);

        Optional<Point> choice = SafePointChoice.nearest(SELF, Optional.of(HOME), Optional.of(campAway));

        assertEquals(Optional.of(HOME), choice,
                "При равных расстояниях обязана побеждать штатная точка home: тай-брейк должен быть устойчивым, "
                        + "иначе выбор прыгал бы между точками от прогона к прогону");
    }

    @Test
    void nearest_NoPointsPresent_ReturnsEmpty() {
        Optional<Point> choice = SafePointChoice.nearest(SELF, Optional.empty(), Optional.empty());

        assertEquals(Optional.empty(), choice,
                "Без дома и лагеря выбора нет: обязан вернуться пусто, чтобы отход упал с явной ошибкой, "
                        + "а не пошёл в случайную точку мира");
    }

    @Test
    void nearest_NullSelf_IsRejected() {
        assertThrows(NullPointerException.class,
                () -> SafePointChoice.nearest(null, Optional.of(HOME), Optional.empty()),
                "Self-точка обязана быть задана: без позиции духа расстояние не посчитать, "
                        + "а молчаливая подмена привела бы к выбору не той точки");
    }

    @Test
    void nearest_NullHome_IsRejected() {
        assertThrows(NullPointerException.class,
                () -> SafePointChoice.nearest(SELF, null, Optional.of(CAMP)),
                "Отсутствие дома обязано приходить как Optional.empty(), а не null: null скрыл бы ошибку "
                        + "сборщика данных и увёл бы отход не туда");
    }
}
