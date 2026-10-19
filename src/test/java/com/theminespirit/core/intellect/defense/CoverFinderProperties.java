package com.theminespirit.core.intellect.defense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class CoverFinderProperties {
    @Provide
    Arbitrary<Point> points() {
        return Combinators.combine(
                        Arbitraries.integers().between(-12, 12),
                        Arbitraries.integers().between(-12, 12))
                .as((x, z) -> new Point(x, 64, z));
    }

    @Provide
    Arbitrary<List<Point>> blockerLists() {
        return points().list().ofMinSize(0).ofMaxSize(5);
    }

    @Provide
    Arbitrary<Double> corridors() {
        return Arbitraries.doubles().between(0.0, 3.0);
    }

    @Property
    void between_AnyInput_SelectedFromInput(
            @ForAll("points") Point self,
            @ForAll("points") Point threat,
            @ForAll("blockerLists") List<Point> blockers,
            @ForAll("corridors") double corridor) {
        Optional<Point> cover = CoverFinder.between(self, threat, blockers, corridor);

        assertTrue(
                cover.isEmpty() || blockers.contains(cover.orElseThrow()),
                "Выбранное укрытие — из входа: иначе боец пойдёт к точке, которой нет в мире");
    }

    @Property
    void between_AnyInput_ProjectionBetweenEnds(
            @ForAll("points") Point self,
            @ForAll("points") Point threat,
            @ForAll("blockerLists") List<Point> blockers,
            @ForAll("corridors") double corridor) {
        Optional<Point> cover = CoverFinder.between(self, threat, blockers, corridor);

        if (cover.isPresent()) {
            Point chosen = cover.orElseThrow();
            double toSelf = dot(chosen, self, threat, true);
            double toThreat = dot(chosen, self, threat, false);
            assertTrue(
                    toSelf >= 0 && toThreat <= 0,
                    "Укрытие лежит между духом и угрозой: иначе линия обстрела проходит мимо кандидата");
        }
    }

    @Property
    void between_ShuffledInput_SameResult(
            @ForAll("points") Point self,
            @ForAll("points") Point threat,
            @ForAll("blockerLists") List<Point> blockers,
            @ForAll("corridors") double corridor) {
        List<Point> shuffled = new ArrayList<>(blockers);
        Collections.shuffle(shuffled, new Random(17));

        assertEquals(
                CoverFinder.between(self, threat, blockers, corridor),
                CoverFinder.between(self, threat, shuffled, corridor),
                "Укрытие не зависит от порядка входа: иначе боец мечется между кандидатами");
    }

    private static double dot(Point candidate, Point self, Point threat, boolean fromSelf) {
        double dx = threat.x() - self.x();
        double dy = threat.y() - self.y();
        double dz = threat.z() - self.z();
        double rx = candidate.x() - (fromSelf ? self.x() : threat.x());
        double ry = candidate.y() - (fromSelf ? self.y() : threat.y());
        double rz = candidate.z() - (fromSelf ? self.z() : threat.z());
        return rx * dx + ry * dy + rz * dz;
    }
}
