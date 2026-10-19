package com.theminespirit.core.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.Point;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CampSpotTests {
    private static final double STANDOFF = 8;

    @Test
    void choose_TargetEastOfSpirit_CampBetweenThemAtStandoff() {
        Point self = new Point(0, 64, 0);
        Point objective = new Point(20, 64, 0);

        Optional<Point> camp = CampSpot.choose(self, Optional.of(objective), Optional.empty(), STANDOFF);

        assertTrue(
                camp.isPresent(), "Лагерь при далёкой цели обязан ставиться: иначе поход останется без точки отхода");
        assertEquals(
                8,
                camp.get().distanceTo(objective),
                1e-6,
                "Лагерь обязан стоять в 8 блоках от цели: дальше — долго идти, ближе — лагерь на голове босса");
        assertTrue(
                camp.get().x() < objective.x() && camp.get().x() > self.x(),
                "Лагерь обязан стоять между духом и целью со стороны духа: иначе это не заходная база, а тыл");
    }

    @Test
    void choose_TargetAdjacent_CampPushedToMinimum() {
        Point self = new Point(0, 64, 0);
        Point objective = new Point(1, 64, 0);

        Optional<Point> camp = CampSpot.choose(self, Optional.of(objective), Optional.empty(), STANDOFF);

        assertTrue(
                camp.isPresent(),
                "Лагерь при цели вплотную обязан ставиться: иначе дух в упор к боссу останется без базы");
        assertEquals(
                4,
                camp.get().distanceTo(objective),
                1e-6,
                "Лагерь при цели вплотную обязан отодвинуться до минимума 4: иначе встанет на саму цель");
    }

    @Test
    void choose_NoObjective_GivesEmpty() {
        Optional<Point> camp = CampSpot.choose(new Point(0, 64, 0), Optional.empty(), Optional.empty(), STANDOFF);

        assertEquals(
                Optional.empty(),
                camp,
                "Без цели лагерь не ставится: иначе точка похода появится в случайном месте без смысла");
    }

    @Test
    void choose_SpiritOnObjective_GivesEmpty() {
        Point same = new Point(5, 64, 5);

        Optional<Point> camp = CampSpot.choose(same, Optional.of(same), Optional.empty(), STANDOFF);

        assertEquals(
                Optional.empty(),
                camp,
                "Дух на голове цели не даёт стороны для лагеря: точка на себе — не заходная база");
    }

    @Test
    void choose_HomeTooClose_CampMovedAwayFromHome() {
        Point self = new Point(0, 64, 0);
        Point objective = new Point(20, 64, 0);
        Point home = new Point(13, 64, 0);

        Optional<Point> camp = CampSpot.choose(self, Optional.of(objective), Optional.of(home), STANDOFF);

        assertTrue(
                camp.isPresent(),
                "Лагерь при близком доме обязан сдвигаться, а не пропадать: иначе поход с домом рядом без базы");
        assertTrue(
                camp.get().distanceTo(home) >= STANDOFF - 1e-6,
                "Лагерь обязан стоять не ближе заходной дистанции от дома: иначе две точки покоя сливаются в одну");
        assertTrue(
                camp.get().distanceTo(objective) >= CampTuning.CAMP_MIN_OBJECTIVE_DISTANCE - 1e-6,
                "Сдвинутый от дома лагерь обязан держать минимум до цели: иначе сдвиг ставит его на босса");
    }

    @Test
    void choose_HomeFar_DeterministicChoice() {
        Point self = new Point(0, 64, 0);
        Point objective = new Point(20, 64, 0);

        Optional<Point> withoutHome = CampSpot.choose(self, Optional.of(objective), Optional.empty(), STANDOFF);
        Optional<Point> withHome =
                CampSpot.choose(self, Optional.of(objective), Optional.of(new Point(-100, 64, -100)), STANDOFF);

        assertEquals(
                withoutHome,
                withHome,
                "Далёкий дом обязан не менять точку лагеря: иначе лагерь прыгает от не связанной с походом точки");
        assertEquals(
                CampSpot.choose(self, Optional.of(objective), Optional.empty(), STANDOFF),
                withoutHome,
                "Тот же вход даёт ту же точку: иначе лагерь мигает между тиками и отход не знает, куда идти");
    }

    @Test
    void choose_NullInput_Throws() {
        Point self = new Point(0, 64, 0);
        Optional<Point> objective = Optional.of(new Point(20, 64, 0));

        assertThrows(
                IllegalArgumentException.class,
                () -> CampSpot.choose(null, objective, Optional.empty(), STANDOFF),
                "Постановка без духа обязана отвергаться: без стороны духа лагерю негде встать");
        assertThrows(
                IllegalArgumentException.class,
                () -> CampSpot.choose(self, null, Optional.empty(), STANDOFF),
                "Постановка без контейнера цели обязана отвергаться: null вместо Optional.empty скрывает баги");
        assertThrows(
                IllegalArgumentException.class,
                () -> CampSpot.choose(self, objective, null, STANDOFF),
                "Постановка без контейнера дома обязана отвергаться: null вместо Optional.empty скрывает баги");
    }

    @Test
    void choose_StandoffBelowMinimum_Throws() {
        Point self = new Point(0, 64, 0);
        Optional<Point> objective = Optional.of(new Point(20, 64, 0));

        assertThrows(
                IllegalArgumentException.class,
                () -> CampSpot.choose(self, objective, Optional.empty(), 2),
                "Заходная дистанция ниже минимума до цели обязана отвергаться: иначе лагерь встанет на саму цель");
        assertThrows(
                IllegalArgumentException.class,
                () -> CampSpot.choose(self, objective, Optional.empty(), Double.NaN),
                "NaN-дистанция обязана отвергаться: иначе сравнение границы даст ложь на любой точке");
    }
}
