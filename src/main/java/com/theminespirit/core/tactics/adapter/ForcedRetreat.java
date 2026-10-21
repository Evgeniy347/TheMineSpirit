package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.TacticalOutcome;

public final class ForcedRetreat {
    private ForcedRetreat() {}

    public static TacticalOutcome apply(
            TacticalOutcome next, CombatDirective directive, RetreatMethod forcedMethod, int enemies) {
        if (directive == null || directive.retreat().isEmpty()) {
            return next;
        }
        if (enemies == 0) {
            return next;
        }
        boolean ordered = directive.mode() == CombatMode.PASSIVE;
        if (next.decision() != TacticalOption.RETREAT && !ordered) {
            return next;
        }
        RetreatMethod method = forcedMethod != RetreatMethod.NONE
                ? forcedMethod
                : next.retreatMethod() != RetreatMethod.NONE ? next.retreatMethod() : RetreatMethod.AWAY;
        if (next.decision() == TacticalOption.RETREAT && next.retreatMethod() == method) {
            return next;
        }
        return new TacticalOutcome(
                TacticalOption.RETREAT,
                next.action(),
                method,
                next.callHelp(),
                next.state(),
                next.mode(),
                next.returnTo(),
                next.trace(),
                next.memory());
    }
}
