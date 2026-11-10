package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.policy.ExplosionAvoidance;
import com.theminespirit.core.tactics.EnemyFact;
import java.util.Optional;
import java.util.Set;

final class SquadStance {
    private SquadStance() {}

    static CombatStance of(DefenseMember member, DefenseSquad squad, ThreatCluster cluster, DefenseProfile profile) {
        if (member.hpFraction() < DefenseTuning.LOW_HP_FRACTION) {
            return member.has(DefensePotion.HEAL) ? CombatStance.HEAL : CombatStance.WITHDRAW;
        }
        Optional<RankedTarget> target = TargetRanker.best(member.role(), squad.hasBow(), cluster.enemies(), profile);
        if (target.isEmpty()) {
            return CombatStance.ENGAGE;
        }
        return InstinctRules.decide(
                        member.capability(), target.get(), cluster.enemies(), squad.capabilities(), Set.of(), profile)
                .stance();
    }

    static Optional<EnemyFact> best(
            DefenseMember member, DefenseSquad squad, ThreatCluster cluster, DefenseProfile profile) {
        return TargetRanker.best(member.role(), squad.hasBow(), cluster.enemies(), profile)
                .map(RankedTarget::enemy);
    }

    static Optional<DefensePotion> potion(
            DefenseMember member,
            CombatStance stance,
            Optional<EnemyFact> target,
            DefenseProfile profile,
            DefenseWeights weights) {
        if (stance == CombatStance.HEAL) {
            return Optional.of(DefensePotion.HEAL);
        }
        if (target.isEmpty()) {
            return Optional.empty();
        }
        TargetClass cls = profile.classify(target.get().type());
        if (member.hpFraction() < weights.healPotionMinHealth() && member.has(DefensePotion.HEAL)) {
            return Optional.of(DefensePotion.HEAL);
        }
        if (cls == TargetClass.CREEPER || cls == TargetClass.HEAVY) {
            if (member.has(DefensePotion.STRENGTH)) {
                return Optional.of(DefensePotion.STRENGTH);
            }
        }
        if ((cls == TargetClass.SKELETON || cls == TargetClass.RANGED) && !member.bow()) {
            if (member.has(DefensePotion.SPEED)) {
                return Optional.of(DefensePotion.SPEED);
            }
        }
        return Optional.empty();
    }

    static Optional<Double> hold(CombatStance stance) {
        if (stance == CombatStance.STRIKE_AND_RETREAT) {
            return Optional.of(ExplosionAvoidance.CREEPER_HOLD);
        }
        return Optional.empty();
    }
}
