package com.theminespirit.core.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.Point;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class CampSpotProperties {
    private static final double STANDOFF = 8;

    @Provide
    Arbitrary<Point> points() {
        return Combinators.combine(
                        Arbitraries.integers().between(-60, 60),
                        Arbitraries.integers().between(0, 128),
                        Arbitraries.integers().between(-60, 60))
                .as((x, y, z) -> new Point(x, y, z));
    }

    @Property
    void choose_AnyObjective_CampWithinReachInterval(@ForAll("points") Point self, @ForAll("points") Point objective) {
        Optional<Point> camp = CampSpot.choose(self, Optional.of(objective), Optional.empty(), STANDOFF);

        if (camp.isEmpty()) {
            assertTrue(
                    self.distanceTo(objective) < 1e-6,
                    "Пусто без совпадения духа и цели: иначе поход теряет базу без причины");
        } else {
            double distance = camp.get().distanceTo(objective);
            assertTrue(
                    distance >= CampTuning.CAMP_MIN_OBJECTIVE_DISTANCE - 1e-6 && distance <= STANDOFF + 1e-6,
                    "Лагерь обязан стоять в [4, 8] от цели: ближе — на голове босса, дальше — не заходная база, "
                            + "получено " + distance);
        }
    }

    @Property
    void choose_ObjectiveOnSelf_GivesEmpty(@ForAll("points") Point self) {
        assertEquals(
                Optional.empty(),
                CampSpot.choose(self, Optional.of(self), Optional.empty(), STANDOFF),
                "Цель на духе не даёт стороны: точка на себе — не лагерь, а ловушка для отхода");
    }

    @Property
    void choose_SameInput_TwiceSameResult(@ForAll("points") Point self, @ForAll("points") Point objective) {
        assertEquals(
                CampSpot.choose(self, Optional.of(objective), Optional.empty(), STANDOFF),
                CampSpot.choose(self, Optional.of(objective), Optional.empty(), STANDOFF),
                "Тот же вход даёт ту же точку: иначе лагерь мигает между пересборками");
    }
}
