package com.theminespirit.core.coordinator;

import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.TargetSelector;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class CoordinatorDirectives {
    private CoordinatorDirectives() {}

    public static Map<UUID, CombatDirective> of(CoordinatorPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("plan не может быть null: приказ координатора собирается из плана");
        }
        if (plan.defendedPoint().isEmpty() && plan.assists().isEmpty()) {
            return Map.of();
        }
        Map<UUID, CombatDirective> directives = new LinkedHashMap<>();
        for (DefenseAssignment assignment : plan.defense()) {
            directives.put(assignment.defender(), defense(assignment));
        }
        for (AssistOrder order : plan.assists()) {
            directives.put(order.helper(), assist(order));
        }
        return Map.copyOf(directives);
    }

    private static CombatDirective defense(DefenseAssignment assignment) {
        return new CombatDirective(
                CombatMode.GUARD_AREA,
                Optional.empty(),
                Optional.of(RetreatMethod.HOME),
                Optional.of(assignment.post()));
    }

    private static CombatDirective assist(AssistOrder order) {
        return CombatDirective.defend(new TargetSelector.Target(
                TargetSelector.Kind.SPIRIT, Optional.of(order.ward().toString())));
    }
}
