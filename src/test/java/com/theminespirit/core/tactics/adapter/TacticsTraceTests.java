package com.theminespirit.core.tactics.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.TacticalAction;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.TacticalOutcome;
import com.theminespirit.core.tactics.TacticalState;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TacticsTraceTests {
    private static TacticalOutcome outcome() {
        TacticalOutcome base =
                AdapterFixtures.outcome(TacticalOption.RETREAT, TacticalAction.RETREAT, RetreatMethod.COVER);
        return new TacticalOutcome(
                base.decision(),
                base.action(),
                base.retreatMethod(),
                true,
                TacticalState.RETREATING,
                CombatMode.GUARD_AREA,
                Optional.of(new Point(1.5, 64, -3)),
                base.trace(),
                base.memory());
    }

    @Test
    void format_Outcome_NamesEveryValue() {
        String text = TacticsTrace.format(outcome());

        for (String expected : new String[] {
            "RETREAT", "COVER", "RETREATING", "GUARD_AREA", "(1.5, 64.0, -3.0)", "true", "решение по правилу score"
        }) {
            assertTrue(
                    text.contains(expected),
                    "Запись решения в лог обязана содержать «" + expected
                            + "»: по ней разбирают, почему дух поступил так; получили: " + text);
        }
    }

    @Test
    void format_EveryRecordComponent_HasLabel() {
        int components = TacticalOutcome.class.getRecordComponents().length;

        assertEquals(
                components,
                TacticsTrace.LABELS.size(),
                "У TacticalOutcome " + components + " полей, а подписей в записи " + TacticsTrace.LABELS.size()
                        + ": новое поле решения обязано попасть в лог, иначе его причину не разобрать");
    }

    @Test
    void format_OutcomeWithoutReturnTarget_SaysNone() {
        String text = TacticsTrace.format(
                AdapterFixtures.outcome(TacticalOption.FIGHT, TacticalAction.ENGAGE, RetreatMethod.NONE));

        assertTrue(
                text.contains("вернуться к=нет"),
                "Без цели возврата запись говорит «нет», а не падает на пустом Optional: " + text);
    }

    @Test
    void format_Null_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TacticsTrace.format(null),
                "Форматировать пустое решение — ошибка вызова, а не пустая строка");
    }
}
