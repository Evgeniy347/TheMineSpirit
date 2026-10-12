package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.TacticalOutcome;
import java.util.Optional;

public final class TargetDesignation {
    private static final IntentActionExecutor INTENT = new IntentActionExecutor();

    private TargetDesignation() {
    }

    public static Optional<EnemyFact> pick(TacticalOutcome outcome, SituationContext context) {
        if (outcome == null || context == null) {
            throw new IllegalArgumentException("TargetDesignation.pick: outcome/context = null; цель выбирают по "
                    + "решению ядра и снятым фактам");
        }
        if (outcome.mode() == CombatMode.PASSIVE) {
            return Optional.empty();
        }
        return switch (INTENT.execute(outcome, context)) {
            case ExecutionResult.Attacking attacking -> Optional.of(attacking.enemy());
            case ExecutionResult.Shooting shooting -> Optional.of(shooting.enemy());
            default -> Optional.empty();
        };
    }
}
