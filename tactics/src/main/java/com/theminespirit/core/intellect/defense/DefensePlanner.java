package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.coordinator.CoordinatorState;
import com.theminespirit.core.coordinator.DefenseAssignment;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.EnemyProfiles;
import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DefensePlanner {
    private DefensePlanner() {}

    public static DefensePlan plan(
            CoordinatorState state,
            List<DefenderCapability> defenders,
            List<EnemyFact> enemies,
            EnemyProfiles profiles,
            DefenseProfile profile) {
        requireInputs(state, defenders, enemies, profiles, profile);
        if (state.defendedPoint().isEmpty()) {
            return new DefensePlan(Optional.empty(), List.of(), List.of());
        }
        Point center = state.defendedPoint().orElseThrow();
        List<EnemyFact> visible = trim(enemies, center);
        DefenseWeights weights = DefenseWeights.loadDefault();
        List<DefenseSquad> squads =
                SquadClusters.group(defenders.stream().map(DefenseMember::of).toList(), weights.squadRadius());
        if (squads.size() > 1) {
            return team(
                    center,
                    state.defendedRadius(),
                    TeamDefensePlanner.plan(
                            center,
                            defenders.stream().map(DefenseMember::of).toList(),
                            visible,
                            profiles,
                            profile,
                            weights));
        }
        List<DefenseAssignment> posts =
                DefenseAllocator.allocate(center, state.defendedRadius(), defenders, visible, profiles);
        if (visible.isEmpty()) {
            return new DefensePlan(Optional.of(center), posts, List.of());
        }
        boolean archerPresent = defenders.stream().anyMatch(DefenderCapability::bow);
        List<DefenderCapability> ordered = defenders.stream()
                .sorted(Comparator.comparing(DefenderCapability::id))
                .toList();
        Set<String> claimed = new HashSet<>();
        Map<UUID, RankedTarget> assigned = new LinkedHashMap<>();
        for (DefenderCapability defender : ordered) {
            List<EnemyFact> eligible = new ArrayList<>();
            for (EnemyFact enemy : visible) {
                if (claimed.contains(enemy.id())) {
                    continue;
                }
                if (defender.role() == DefenderRole.MELEE && profile.classify(enemy.type()) == TargetClass.CREEPER) {
                    continue;
                }
                eligible.add(enemy);
            }
            Optional<RankedTarget> pick = TargetRanker.best(defender.role(), archerPresent, eligible, profile);
            if (pick.isPresent()) {
                claimed.add(pick.get().enemy().id());
                assigned.put(defender.id(), pick.get());
            }
        }
        assignLoneCreeper(center, ordered, visible, claimed, assigned, profile, archerPresent);
        List<DefenderOrder> orders = new ArrayList<>(ordered.size());
        for (DefenderCapability defender : ordered) {
            RankedTarget target = assigned.get(defender.id());
            if (target == null) {
                orders.add(new DefenderOrder(
                        defender.id(),
                        defender.role(),
                        Optional.empty(),
                        CombatStance.ENGAGE,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        "свободных целей нет: держит пост"));
                continue;
            }
            InstinctDecision decision = InstinctRules.decide(defender, target, visible, defenders, Set.of(), profile);
            orders.add(new DefenderOrder(
                    defender.id(),
                    defender.role(),
                    Optional.of(target.enemy()),
                    decision.stance(),
                    decision.holdDistance(),
                    decision.cover(),
                    decision.handOff(),
                    decision.reason()));
        }
        return new DefensePlan(Optional.of(center), posts, List.copyOf(orders));
    }

    private static DefensePlan team(Point center, double radius, TeamDefensePlan plan) {
        Map<UUID, DefenseMember> byId = new LinkedHashMap<>();
        for (DefenseSquad squad : plan.squads()) {
            for (DefenseMember member : squad.members()) {
                byId.put(member.id(), member);
            }
        }
        List<DefenseAssignment> posts = new ArrayList<>(plan.memberOrders().size());
        List<DefenderOrder> orders = new ArrayList<>(plan.memberOrders().size());
        Set<String> claimed = new HashSet<>();
        for (MemberOrder order : plan.memberOrders()) {
            DefenseMember member = byId.get(order.member());
            if (member == null) {
                throw new IllegalStateException(
                        "дух " + order.member() + " получил приказ, но не входит ни в один отряд: снимок разошёлся");
            }
            Point post = order.post().distanceTo(center) <= radius
                    ? order.post()
                    : SquadPlacement.toward(center, order.post(), radius);
            posts.add(new DefenseAssignment(order.member(), post, SquadPlacement.bearing(center, post)));
            Optional<EnemyFact> target = order.target().filter(enemy -> claimed.add(enemy.id()));
            orders.add(new DefenderOrder(
                    order.member(),
                    member.role(),
                    target,
                    order.stance(),
                    order.holdDistance(),
                    Optional.empty(),
                    Optional.empty(),
                    order.potion(),
                    order.reason()));
        }
        return new DefensePlan(Optional.of(center), posts, orders);
    }

    private static void requireInputs(
            CoordinatorState state,
            List<DefenderCapability> defenders,
            List<EnemyFact> enemies,
            EnemyProfiles profiles,
            DefenseProfile profile) {
        if (state == null) {
            throw new IllegalArgumentException("state не может быть null: точка обороны берётся из состояния");
        }
        if (defenders == null) {
            throw new IllegalArgumentException("defenders не может быть null: нет защитников — пустой список");
        }
        if (enemies == null) {
            throw new IllegalArgumentException("enemies не может быть null: нет врагов — пустой список");
        }
        if (profiles == null) {
            throw new IllegalArgumentException("profiles не может быть null: сила врага берётся из enemies.json");
        }
        if (profile == null) {
            throw new IllegalArgumentException("profile не может быть null: цели выбираются по таблице приоритетов");
        }
        if (defenders.stream().anyMatch(defender -> defender == null)) {
            throw new IllegalArgumentException("defenders содержит null: защитник без снимка приказ не получит");
        }
        if (enemies.stream().anyMatch(enemy -> enemy == null)) {
            throw new IllegalArgumentException("enemies содержит null: враг без факта в план не попадёт");
        }
    }

    private static void assignLoneCreeper(
            Point center,
            List<DefenderCapability> ordered,
            List<EnemyFact> visible,
            Set<String> claimed,
            Map<UUID, RankedTarget> assigned,
            DefenseProfile profile,
            boolean archerPresent) {
        List<EnemyFact> creepers = new ArrayList<>();
        for (EnemyFact enemy : visible) {
            if (!claimed.contains(enemy.id()) && profile.classify(enemy.type()) == TargetClass.CREEPER) {
                creepers.add(enemy);
            }
        }
        if (creepers.isEmpty()) {
            return;
        }
        creepers.sort(
                Comparator.comparingDouble((EnemyFact enemy) -> enemy.pos().distanceTo(center))
                        .thenComparing(EnemyFact::id));
        if (archerPresent && creepers.get(0).pos().distanceTo(center) > DefenseTuning.RUSH_MARGIN) {
            return;
        }
        Optional<DefenderCapability> champion = ordered.stream()
                .filter(defender -> !assigned.containsKey(defender.id()) && defender.role() == DefenderRole.MELEE)
                .sorted(Comparator.comparingDouble(DefenderCapability::frontRank)
                        .reversed()
                        .thenComparing(DefenderCapability::id))
                .findFirst();
        if (champion.isEmpty()) {
            return;
        }
        EnemyFact creeper = creepers.get(0);
        int tail = profile.orderFor(DefenderRole.MELEE, archerPresent).size();
        assigned.put(
                champion.get().id(),
                new RankedTarget(
                        creeper, TargetClass.CREEPER, tail, creeper.pos().distanceTo(center)));
        claimed.add(creeper.id());
    }

    private static List<EnemyFact> trim(List<EnemyFact> enemies, Point center) {
        List<EnemyFact> sorted = new ArrayList<>(enemies);
        sorted.sort(Comparator.comparingDouble((EnemyFact enemy) -> enemy.pos().distanceTo(center))
                .thenComparing(EnemyFact::type)
                .thenComparingDouble(EnemyFact::hpFraction)
                .thenComparing(EnemyFact::id));
        return List.copyOf(sorted.subList(0, Math.min(sorted.size(), DefenseTuning.MAX_ENEMIES)));
    }
}
