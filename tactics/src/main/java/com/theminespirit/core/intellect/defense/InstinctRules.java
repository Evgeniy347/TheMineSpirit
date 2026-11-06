package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.policy.ExplosionAvoidance;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class InstinctRules {
    private InstinctRules() {}

    public static InstinctDecision decide(
            DefenderCapability self,
            RankedTarget target,
            List<EnemyFact> enemies,
            List<DefenderCapability> allies,
            Set<UUID> busy,
            DefenseProfile profile) {
        requireInputs(self, target, enemies, allies, busy, profile);
        boolean wounded = self.hpFraction() < DefenseTuning.LOW_HP_FRACTION;
        if (wounded && self.heal()) {
            return new InstinctDecision(
                    CombatStance.HEAL,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    "здоровье низкое, есть лечилка: дух отходит и лечится");
        }
        if (wounded) {
            Optional<UUID> next = TargetHandoff.handOff(self.id(), target.enemy(), allies, busy, profile);
            return new InstinctDecision(
                    CombatStance.WITHDRAW,
                    Optional.empty(),
                    Optional.empty(),
                    next,
                    "здоровье низкое, лечилки нет: дух отходит и передаёт цель");
        }
        boolean archerPresent = self.bow()
                || allies.stream()
                        .anyMatch(ally -> ally.bow()
                                && !busy.contains(ally.id())
                                && ally.hpFraction() >= DefenseTuning.LOW_HP_FRACTION);
        if (target.cls() == TargetClass.CREEPER && self.role() == DefenderRole.MELEE) {
            return new InstinctDecision(
                    CombatStance.STRIKE_AND_RETREAT,
                    Optional.of(ExplosionAvoidance.CREEPER_HOLD),
                    Optional.empty(),
                    Optional.empty(),
                    "мечник против крипера: удар и отскок за радиус взрыва");
        }
        if (target.cls() == TargetClass.SKELETON && self.role() == DefenderRole.MELEE && !archerPresent) {
            return new InstinctDecision(
                    CombatStance.RUSH_RANGED,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    "лучника нет: мечник быстро сближается со скелетом");
        }
        if (target.cls() == TargetClass.MELEE) {
            Optional<EnemyFact> skeleton = nearestSkeleton(self.pos(), enemies, profile);
            if (skeleton.isPresent()) {
                Optional<Point> cover = CoverFinder.between(
                        self.pos(),
                        skeleton.get().pos(),
                        blockers(enemies, skeleton.get()),
                        DefenseTuning.COVER_CORRIDOR);
                if (cover.isPresent()) {
                    return new InstinctDecision(
                            CombatStance.COVER_ENGAGE,
                            Optional.empty(),
                            cover,
                            Optional.empty(),
                            "скелет рядом: бой с ближней целью из-за укрытия");
                }
            }
        }
        if (self.role() == DefenderRole.ARCHER) {
            return new InstinctDecision(
                    CombatStance.HOLD_DISTANCE,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    "лучник держит дистанцию и обстреливает цель");
        }
        return new InstinctDecision(
                CombatStance.ENGAGE,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "ближний бой с выбранной целью");
    }

    private static void requireInputs(
            DefenderCapability self,
            RankedTarget target,
            List<EnemyFact> enemies,
            List<DefenderCapability> allies,
            Set<UUID> busy,
            DefenseProfile profile) {
        if (self == null) {
            throw new IllegalArgumentException("self не может быть null: стойка выбирается конкретному духу");
        }
        if (target == null) {
            throw new IllegalArgumentException("target не может быть null: инстинкт действует поверх цели");
        }
        if (enemies == null) {
            throw new IllegalArgumentException("enemies не может быть null: нет врагов — пустой список");
        }
        if (allies == null) {
            throw new IllegalArgumentException("allies не может быть null: нет союзников — пустой список");
        }
        if (busy == null) {
            throw new IllegalArgumentException("busy не может быть null: нет занятых — пустое множество");
        }
        if (profile == null) {
            throw new IllegalArgumentException(
                    "profile не может быть null: класс скелета рядом считается по таблице приоритетов");
        }
        if (enemies.stream().anyMatch(enemy -> enemy == null)) {
            throw new IllegalArgumentException("enemies содержит null: враг без факта не проверяется");
        }
        if (allies.stream().anyMatch(ally -> ally == null)) {
            throw new IllegalArgumentException("allies содержит null: союзник без снимка не проверяется");
        }
    }

    private static Optional<EnemyFact> nearestSkeleton(Point self, List<EnemyFact> enemies, DefenseProfile profile) {
        EnemyFact nearest = null;
        double nearestDistance = 0;
        for (EnemyFact enemy : enemies) {
            if (profile.classify(enemy.type()) != TargetClass.SKELETON) {
                continue;
            }
            double distance = enemy.pos().distanceTo(self);
            if (distance > DefenseTuning.RUSH_MARGIN) {
                continue;
            }
            if (nearest == null
                    || distance < nearestDistance
                    || (distance == nearestDistance && enemy.id().compareTo(nearest.id()) < 0)) {
                nearest = enemy;
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    private static List<Point> blockers(List<EnemyFact> enemies, EnemyFact skeleton) {
        List<Point> blockers = new ArrayList<>();
        for (EnemyFact enemy : enemies) {
            if (!enemy.id().equals(skeleton.id())) {
                blockers.add(enemy.pos());
            }
        }
        return blockers;
    }
}
