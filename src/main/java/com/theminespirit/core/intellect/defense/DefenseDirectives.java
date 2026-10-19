package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.tactics.CombatDirective;
import com.theminespirit.core.tactics.CombatMode;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.TargetSelector;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DefenseDirectives {
    private DefenseDirectives() {}

    public static Map<UUID, DefenseIssuedOrder> of(DefensePlan plan) {
        return of(plan, Set.of());
    }

    public static Map<UUID, DefenseIssuedOrder> of(DefensePlan plan, Set<String> chargedEnemyIds) {
        if (plan == null) {
            throw new IllegalArgumentException("plan не может быть null: приказ обороны собирается из плана");
        }
        if (chargedEnemyIds == null) {
            throw new IllegalArgumentException("chargedEnemyIds не может быть null: нет заряженных — пустое множество");
        }
        if (plan.defendedPoint().isEmpty()) {
            return Map.of();
        }
        Map<UUID, DefenderOrder> byDefender = new LinkedHashMap<>();
        for (DefenderOrder order : plan.orders()) {
            byDefender.put(order.defender(), order);
        }
        Map<UUID, DefenseIssuedOrder> issued = new LinkedHashMap<>();
        for (DefenderOrder order : plan.orders()) {
            issued.put(order.defender(), direct(order, plan, chargedEnemyIds));
        }
        for (var assignment : plan.posts()) {
            issued.putIfAbsent(
                    assignment.defender(),
                    new DefenseIssuedOrder(assignment.defender(), post(assignment.post()), Optional.empty()));
        }
        for (DefenderOrder order : plan.orders()) {
            if (order.handOff().isEmpty() || order.target().isEmpty()) {
                continue;
            }
            UUID ally = order.handOff().get();
            DefenderOrder allyOrder = byDefender.get(ally);
            if (allyOrder == null
                    || allyOrder.defender().equals(order.defender())
                    || allyOrder.target().isPresent()) {
                continue;
            }
            issued.put(
                    ally,
                    new DefenseIssuedOrder(
                            ally, attack(), Optional.of(order.target().get().id())));
        }
        return Map.copyOf(issued);
    }

    private static DefenseIssuedOrder direct(DefenderOrder order, DefensePlan plan, Set<String> charged) {
        if (order.target().isEmpty()) {
            return new DefenseIssuedOrder(order.defender(), post(postFor(order, plan)), Optional.empty());
        }
        String enemyId = order.target().get().id();
        if (order.stance() == CombatStance.WITHDRAW || order.stance() == CombatStance.HEAL) {
            return new DefenseIssuedOrder(order.defender(), retreat(plan), Optional.empty());
        }
        if (order.role() == DefenderRole.MELEE && charged.contains(enemyId)) {
            return new DefenseIssuedOrder(order.defender(), retreat(plan), Optional.empty());
        }
        return new DefenseIssuedOrder(order.defender(), attack(), Optional.of(enemyId));
    }

    private static CombatDirective post(Point post) {
        return new CombatDirective(
                CombatMode.GUARD_AREA, Optional.empty(), Optional.of(RetreatMethod.HOME), Optional.of(post));
    }

    private static CombatDirective retreat(DefensePlan plan) {
        return CombatDirective.retreat(plan.defendedPoint().orElseThrow());
    }

    private static CombatDirective attack() {
        return new CombatDirective(
                CombatMode.AGGRESSIVE,
                Optional.of(new TargetSelector.Target(TargetSelector.Kind.HOSTILE, Optional.empty())),
                Optional.empty(),
                Optional.empty());
    }

    private static Point postFor(DefenderOrder order, DefensePlan plan) {
        for (var assignment : plan.posts()) {
            if (assignment.defender().equals(order.defender())) {
                return assignment.post();
            }
        }
        return plan.defendedPoint().orElseThrow();
    }
}
