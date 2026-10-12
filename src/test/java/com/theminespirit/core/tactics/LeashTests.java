package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class LeashTests {
    private static final Modes MODES = TacticsConfig.loadDefault().modes();
    private static final Point ORIGIN = new Point(0, 0, 0);

    private static LeashResult defensive(double self, double target) {
        return Leash.check(MODES.of(CombatMode.DEFENSIVE), 0, ORIGIN, new Point(self, 0, 0), new Point(target, 0, 0));
    }

    @Test
    void check_TargetExactlyAtPursuitRadius_IsWithinWithZeroLeft() {
        assertEquals(new LeashResult.Within(0), defensive(0, 8),
                "Цель ровно на границе преследования ещё внутри: остаток 0, а не «превышено»");
    }

    @Test
    void check_TargetBeyondPursuitRadius_IsExceededByExcess() {
        assertEquals(new LeashResult.Exceeded(0.5), defensive(0, 8.5),
                "Цель за границей преследования не преследуется: превышение 0.5 блока");
    }

    @Test
    void check_SpiritItselfBeyondLeash_IsExceededEvenIfTargetIsClose() {
        assertEquals(new LeashResult.Exceeded(2), defensive(10, 2),
                "Сам дух ушёл дальше поводка, и цель рядом не спасает: пора возвращаться к якорю");
    }

    @Test
    void check_PassiveMode_IsUnboundedBecausePassiveHasNoLeash() {
        LeashResult result = Leash.check(MODES.of(CombatMode.PASSIVE), 0, ORIGIN, ORIGIN, new Point(500, 0, 0));

        assertEquals(new LeashResult.Within(Assessment.UNBOUNDED), result,
                "У пассивного режима поводка нет: он не преследует, граница не нужна");
    }

    @Test
    void check_GuardAreaRadiusFromOrder_ShiftsPursuitBoundary() {
        ModeParams guard = MODES.of(CombatMode.GUARD_AREA);

        assertEquals(new LeashResult.Within(0), Leash.check(guard, 10, ORIGIN, ORIGIN, new Point(14, 0, 0)),
                "Охрана зоны радиуса 10: преследование до 14");
        assertEquals(new LeashResult.Exceeded(1), Leash.check(guard, 10, ORIGIN, ORIGIN, new Point(15, 0, 0)),
                "Цель в 15 блоках от центра зоны радиуса 10 за поводком на 1");
    }

    @Test
    void returnTarget_AfterFightFarFromAnchor_ReturnsAnchorOnlyWhereModeReturns() {
        Point anchor = new Point(10, 0, 0);
        Point far = new Point(15, 0, 0);

        assertEquals(Optional.of(anchor),
                Leash.returnTarget(MODES.of(CombatMode.GUARD_AREA), Optional.of(anchor), far, 2),
                "Охрана зоны возвращается к центру после боя");
        assertEquals(Optional.empty(),
                Leash.returnTarget(MODES.of(CombatMode.AGGRESSIVE), Optional.of(anchor), far, 2),
                "Агрессивный режим не возвращается к якорю: у него returnToAnchor = false");
        assertEquals(Optional.empty(), Leash.returnTarget(MODES.of(CombatMode.GUARD_AREA), Optional.of(anchor),
                new Point(11.5, 0, 0), 2), "Внутри допуска возвращаться не нужно: иначе дух дёргался бы на месте");
    }
}
