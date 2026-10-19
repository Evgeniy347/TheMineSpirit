package com.theminespirit.core.base;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.tactics.Point;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class CampProperties {
    @Provide
    Arbitrary<Camp> camps() {
        return Combinators.combine(
                        Arbitraries.integers().between(-100, 100),
                        Arbitraries.integers().between(-64, 320),
                        Arbitraries.integers().between(-100, 100),
                        Arbitraries.doubles().between(0.5, 64),
                        Arbitraries.integers().between(0, 100),
                        Arbitraries.longs().between(0, 100000))
                .as((x, y, z, radius, owner, setAt) -> new Camp(new Point(x, y, z), radius, owner, setAt));
    }

    @Provide
    Arbitrary<Point> points() {
        return Combinators.combine(
                        Arbitraries.integers().between(-120, 120),
                        Arbitraries.integers().between(-64, 320),
                        Arbitraries.integers().between(-120, 120))
                .as((x, y, z) -> new Point(x, y, z));
    }

    @Property
    void contains_AnyPoint_MatchesBoundaryComparison(@ForAll("camps") Camp camp, @ForAll("points") Point point) {
        double dx = camp.center().x() - point.x();
        double dy = camp.center().y() - point.y();
        double dz = camp.center().z() - point.z();
        boolean expected = Math.sqrt(dx * dx + dy * dy + dz * dz) <= camp.radius();

        assertEquals(
                expected,
                camp.contains(point),
                "contains обязан совпадать с проверкой границы по координатам: иначе спокойная зона "
                        + "расходится с заявленным радиусом и оборона кроет не ту точку");
    }

    @Property
    void contains_SameInput_TwiceSameResult(@ForAll("camps") Camp camp, @ForAll("points") Point point) {
        assertEquals(
                camp.contains(point),
                camp.contains(point),
                "Тот же вход даёт тот же ответ: иначе граница лагеря мигает между тиками");
    }
}
