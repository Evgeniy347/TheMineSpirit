package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.DoubleRange;

class LeashProperties {
    private static final Modes MODES = TacticsConfig.loadDefault().modes();
    private static final Point ORIGIN = new Point(0, 0, 0);

    @Provide
    Arbitrary<CombatMode> leashedModes() {
        return Arbitraries.of(CombatMode.DEFENSIVE, CombatMode.PROTECT, CombatMode.GUARD_AREA, CombatMode.AGGRESSIVE);
    }

    private static LeashResult check(CombatMode mode, double zone, double distance) {
        return Leash.check(MODES.of(mode), zone, ORIGIN, ORIGIN, new Point(distance, 0, 0));
    }

    @Property
    void check_FartherTarget_NeverLeavesMoreRoom(
            @ForAll("leashedModes") CombatMode mode,
            @ForAll @DoubleRange(min = 1, max = 50) double zone,
            @ForAll @DoubleRange(min = 0, max = 80) double near,
            @ForAll @DoubleRange(min = 0, max = 40) double extra) {
        double nearLeft = check(mode, zone, near).left();
        double farLeft = check(mode, zone, near + extra).left();

        assertTrue(
                farLeft <= nearLeft + 1e-12,
                "Чем дальше цель от якоря, тем меньше остаток поводка: " + nearLeft + " → " + farLeft);
    }

    @Property
    void check_TargetBeyondPursuitRadius_IsNeverPursued(
            @ForAll("leashedModes") CombatMode mode,
            @ForAll @DoubleRange(min = 1, max = 50) double zone,
            @ForAll @DoubleRange(min = 0.01, max = 40) double over) {
        double pursuit = MODES.of(mode).pursuit(zone);

        LeashResult result = check(mode, zone, pursuit + over);

        assertInstanceOf(
                LeashResult.Exceeded.class,
                result,
                "Цель дальше радиуса преследования (" + pursuit + " + " + over + ") не преследуется");
    }

    @Property
    void returnTarget_AfterCombat_PresentExactlyWhenFarAndModeReturns(
            @ForAll CombatMode mode, @ForAll @DoubleRange(min = 0, max = 60) double distance) {
        ModeParams params = MODES.of(mode);
        Point anchor = new Point(5, 0, 5);
        Point pos = new Point(5 + distance, 0, 5);
        double tolerance = MODES.returnTolerance();

        Optional<Point> target = Leash.returnTarget(params, Optional.of(anchor), pos, tolerance);

        assertEquals(
                params.returnToAnchor() && distance > tolerance,
                target.isPresent(),
                "После боя дух возвращается к якорю, когда режим это требует и он дальше допуска " + tolerance
                        + ": режим " + mode + ", расстояние " + distance);
    }
}
