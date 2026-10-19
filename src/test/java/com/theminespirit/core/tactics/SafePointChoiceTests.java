package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SafePointChoiceTests {
    private final Point self = new Point(0, 64, 0);

    @Test
    void nearest_OnlyHome_ReturnsHome() {
        Point home = new Point(10, 64, 0);

        assertEquals(
                Optional.of(home),
                SafePointChoice.nearest(self, Optional.of(home), Optional.empty()),
                "Единственный дом обязан выбираться: иначе дух с домом останется без точки отхода");
    }

    @Test
    void nearest_OnlyCamp_ReturnsCamp() {
        Point camp = new Point(5, 64, 0);

        assertEquals(
                Optional.of(camp),
                SafePointChoice.nearest(self, Optional.empty(), Optional.of(camp)),
                "Единственный лагерь обязан выбираться: иначе поход без дома останется без точки отхода");
    }

    @Test
    void nearest_BothPresent_ReturnsNearer() {
        Point home = new Point(30, 64, 0);
        Point camp = new Point(6, 64, 0);

        assertEquals(
                Optional.of(camp),
                SafePointChoice.nearest(self, Optional.of(home), Optional.of(camp)),
                "Из дома и лагеря выбирается ближний: иначе отход идёт мимо ближней точки к дальней");

        assertEquals(
                Optional.of(camp),
                SafePointChoice.nearest(self, Optional.of(camp), Optional.of(home)),
                "Порядок аргументов обязан не влиять: иначе выбор зависит от того, что подали первым");
    }

    @Test
    void nearest_Tie_ReturnsHome() {
        Point home = new Point(10, 64, 0);
        Point camp = new Point(-10, 64, 0);

        assertEquals(
                Optional.of(home),
                SafePointChoice.nearest(self, Optional.of(home), Optional.of(camp)),
                "При ничьей выбирается дом: устойчивый тай-брейк, иначе отход прыгает между точками");
    }

    @Test
    void nearest_BothEmpty_GivesEmpty() {
        assertEquals(
                Optional.empty(),
                SafePointChoice.nearest(self, Optional.empty(), Optional.empty()),
                "Без дома и лагеря точки нет: адаптер отдаёт «точки отхода нет», а не выдуманную точку");
    }

    @Test
    void nearest_NullSelf_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SafePointChoice.nearest(null, Optional.of(new Point(1, 64, 0)), Optional.empty()),
                "Выбор без духа обязан отвергаться: ближе не от чего мерять");
    }

    @Test
    void nearest_NullContainers_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SafePointChoice.nearest(self, null, Optional.empty()),
                "Null вместо контейнера дома обязан отвергаться: иначе баги прячутся за молчанием");
        assertThrows(
                IllegalArgumentException.class,
                () -> SafePointChoice.nearest(self, Optional.empty(), null),
                "Null вместо контейнера лагеря обязан отвергаться: иначе баги прячутся за молчанием");
    }

    @Test
    void nearest_NoCampStubPresentHome_ReturnsHome() {
        Point home = new Point(10, 64, 0);

        assertEquals(
                Optional.of(home),
                SafePointChoice.nearest(self, Optional.of(home), SafePointChoice.NO_CAMP),
                "Заглушка лагеря обязана вести себя как раньше: пока лагеря нет, отход от точки с домом "
                        + "идёт домой, иначе с включённой заглушкой планы отхода сломаются");
        assertTrue(
                SafePointChoice.NO_CAMP.isEmpty(),
                "Заглушка лагеря обязана быть пустой: лагерь в этом чеклисте не реализован, "
                        + "а не-пустая заглушка подставила бы в отход выдуманную точку");
    }

    @Test
    void nearest_NoCampStubWithoutHome_ReturnsEmpty() {
        assertEquals(
                Optional.empty(),
                SafePointChoice.nearest(self, Optional.empty(), SafePointChoice.NO_CAMP),
                "Без дома и с пустой заглушкой лагеря выбора нет: обязан вернуться пусто, как и раньше, "
                        + "чтобы отход упал с явной ошибкой, а не пошёл в случайную точку мира");
    }

    @Test
    void nearest_CampAttachedStubReplaced_ChoosesNearerCamp() {
        Point home = new Point(10, 64, 0);
        Point camp = new Point(5, 64, 0);

        assertEquals(
                Optional.of(camp),
                SafePointChoice.nearest(self, Optional.of(home), Optional.of(camp)),
                "Хук обязан принять лагерь без правок кода: когда вместо заглушки придёт точка лагеря, "
                        + "отход обязан пойти к ближайшей из двух точек");
    }
}
