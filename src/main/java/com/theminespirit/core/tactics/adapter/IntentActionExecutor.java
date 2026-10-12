package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.TacticalAction;
import com.theminespirit.core.tactics.TacticalOutcome;
import java.util.Comparator;
import java.util.List;

public final class IntentActionExecutor implements ActionExecutor {
    @Override
    public ExecutionResult execute(TacticalOutcome outcome, SituationContext context) {
        if (outcome == null) {
            throw new IllegalArgumentException(
                    "IntentActionExecutor.execute: outcome = null; проверьте вызов исполнения решения");
        }
        if (context == null) {
            throw new IllegalArgumentException(
                    "IntentActionExecutor.execute: context = null; проверьте вызов исполнения решения");
        }
        return switch (outcome.decision()) {
            case IGNORE -> ExecutionResult.idle();
            case RETREAT -> retreat(outcome.retreatMethod());
            case FIGHT -> fight(outcome, context);
        };
    }

    private static ExecutionResult retreat(RetreatMethod method) {
        if (method == RetreatMethod.NONE) {
            return ExecutionResult.idle();
        }
        return method == RetreatMethod.BARRICADE || method == RetreatMethod.PILLAR
                ? new ExecutionResult.Building(method)
                : new ExecutionResult.Moving(method);
    }

    private static ExecutionResult fight(TacticalOutcome outcome, SituationContext context) {
        EnemyFact target = primaryTarget(context.enemies(), context.self().pos());
        if (target == null) {
            return ExecutionResult.idle();
        }
        return outcome.action() == TacticalAction.SHOOT
                ? new ExecutionResult.Shooting(target)
                : new ExecutionResult.Attacking(target);
    }

    private static EnemyFact primaryTarget(List<EnemyFact> enemies, Point self) {
        Comparator<EnemyFact> preference = Comparator.comparing((EnemyFact enemy) -> !enemy.visible())
                .thenComparing(enemy -> !enemy.targetingSelf()).thenComparingDouble(enemy -> enemy.pos().distanceTo(self)).thenComparing(EnemyFact::type)
                .thenComparing(EnemyFact::id);
        return enemies.stream().min(preference).orElse(null);
    }
}
