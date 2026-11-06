package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.TacticalOutcome;

public final class RetreatCommitment {
    private RetreatCommitment() {}

    public static RetreatTracker next(
            RetreatTracker current, TacticalOutcome outcome, long nowTick, TacticsAdapterSettings settings) {
        if (current == null || outcome == null || settings == null) {
            throw new IllegalArgumentException("RetreatCommitment.next: current/outcome/settings = null; "
                    + "обязательство отхода обновляют по решению ядра и настройкам адаптера");
        }
        if (outcome.decision() == TacticalOption.FIGHT) {
            return RetreatTracker.IDLE;
        }
        if (replacesFallback(current, outcome)) {
            return new RetreatTracker(outcome.retreatMethod(), nowTick);
        }
        return current.advance(outcome, nowTick, settings);
    }

    private static boolean replacesFallback(RetreatTracker current, TacticalOutcome outcome) {
        return current.method() == RetreatMethod.AWAY
                && outcome.decision() == TacticalOption.RETREAT
                && outcome.retreatMethod() != RetreatMethod.NONE
                && outcome.retreatMethod() != RetreatMethod.AWAY;
    }
}
