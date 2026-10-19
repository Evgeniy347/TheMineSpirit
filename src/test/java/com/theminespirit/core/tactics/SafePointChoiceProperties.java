package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class SafePointChoiceProperties {
    @Provide
    Arbitrary<Point> points() {
        return Combinators.combine(
                        Arbitraries.integers().between(-60, 60),
                        Arbitraries.integers().between(0, 128),
                        Arbitraries.integers().between(-60, 60))
                .as((x, y, z) -> new Point(x, y, z));
    }

    @Property
    void nearest_AnyInput_ResultIsOneOfInputs(
            @ForAll("points") Point self,
            @ForAll("points") Point home,
            @ForAll("points") Point camp,
            @ForAll boolean hasHome,
            @ForAll boolean hasCamp) {
        Optional<Point> homeInput = hasHome ? Optional.of(home) : Optional.empty();
        Optional<Point> campInput = hasCamp ? Optional.of(camp) : Optional.empty();

        Optional<Point> result = SafePointChoice.nearest(self, homeInput, campInput);

        if (!hasHome && !hasCamp) {
            assertEquals(Optional.empty(), result, "Без точек выбора нет: иначе отход идёт в выдуманную точку");
        } else {
            assertTrue(
                    result.isPresent(),
                    "При хотя бы одной точке выбор обязан быть: иначе дух с базой остаётся без отхода");
            assertTrue(
                    result.get().equals(home) || result.get().equals(camp),
                    "Выбор обязан быть одной из входных точек: иначе отход ведёт не к дому и не к лагерю");
        }
    }

    @Property
    void nearest_AnyInput_ResultNotFartherThanAlternative(
            @ForAll("points") Point self, @ForAll("points") Point home, @ForAll("points") Point camp) {
        Optional<Point> result = SafePointChoice.nearest(self, Optional.of(home), Optional.of(camp));

        double chosen = self.distanceTo(result.orElseThrow());
        double other = result.get().equals(home) ? self.distanceTo(camp) : self.distanceTo(home);
        assertTrue(
                chosen <= other, "Выбранная точка обязана быть не дальше другой: иначе отход идёт мимо ближней точки");
    }

    @Property
    void nearest_Tie_ReturnsHome(@ForAll("points") Point self, @ForAll("points") Point home) {
        Point mirrored = new Point(2 * self.x() - home.x(), 2 * self.y() - home.y(), 2 * self.z() - home.z());

        assertEquals(
                Optional.of(home),
                SafePointChoice.nearest(self, Optional.of(home), Optional.of(mirrored)),
                "При равных расстояниях выбирается дом: иначе тай-брейк неустойчив и отход прыгает");
    }

    @Property
    void nearest_SameInput_TwiceSameResult(
            @ForAll("points") Point self, @ForAll("points") Point home, @ForAll("points") Point camp) {
        assertEquals(
                SafePointChoice.nearest(self, Optional.of(home), Optional.of(camp)),
                SafePointChoice.nearest(self, Optional.of(home), Optional.of(camp)),
                "Тот же вход даёт тот же выбор: иначе точка отхода мигает между тиками");
    }
}
