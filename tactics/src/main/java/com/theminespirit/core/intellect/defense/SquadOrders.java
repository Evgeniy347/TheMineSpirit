package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

final class SquadOrders {
    private SquadOrders() {}

    static SquadOrder stay(Point center, DefenseSquad squad, List<MemberOrder> members, DefenseWeights weights) {
        boolean hungry = squad.members().stream().allMatch(member -> member.food() < weights.foodFull());
        SquadDecision decision = hungry ? SquadDecision.EAT : SquadDecision.STAY;
        Point post = hungry ? center : squad.center();
        for (DefenseMember member : squad.members()) {
            members.add(new MemberOrder(
                    member.id(),
                    squad.id(),
                    decision,
                    post,
                    CombatStance.ENGAGE,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    hungry ? "сытость ниже полной: отряд сперва ест" : "угроз рядом нет: отряд остаётся на месте"));
        }
        return new SquadOrder(
                squad.id(),
                decision,
                post,
                SquadPlacement.bearing(center, squad.center()),
                Optional.empty(),
                CombatStance.ENGAGE,
                stances(squad, CombatStance.ENGAGE),
                Map.of(),
                List.of(),
                squad.members().size(),
                0,
                hungry ? "сытость ниже полной: отряд сперва ест" : "угроз рядом нет: отряд остаётся на месте");
    }

    static SquadOrder engage(
            Point center,
            DefenseSquad squad,
            ThreatCluster cluster,
            DefenseProfile profile,
            DefenseWeights weights,
            List<MemberOrder> members) {
        boolean allWounded = !squad.has(DefensePotion.HEAL)
                && share(squad, member -> member.hpFraction() < weights.woundedFraction()) >= weights.allWoundedShare();
        boolean allHungry = squad.members().stream().allMatch(member -> member.food() < weights.foodFull());
        SquadDecision decision =
                allWounded ? SquadDecision.RETREAT : allHungry ? SquadDecision.EAT : SquadDecision.ENGAGE;
        Point post = decision == SquadDecision.ENGAGE ? squad.center() : center;
        Map<UUID, CombatStance> stances = new LinkedHashMap<>();
        Map<UUID, DefensePotion> potions = new LinkedHashMap<>();
        int targets = 0;
        for (DefenseMember member : squad.members()) {
            CombatStance stance = memberStance(member, squad, cluster, decision, profile);
            Optional<EnemyFact> target = decision == SquadDecision.ENGAGE
                    ? SquadStance.best(member, squad, cluster, profile)
                    : Optional.empty();
            Optional<DefensePotion> potion = SquadStance.potion(member, stance, target, profile, weights);
            stances.put(member.id(), stance);
            potion.ifPresent(value -> potions.put(member.id(), value));
            if (target.isPresent()) {
                targets++;
            }
            members.add(new MemberOrder(
                    member.id(),
                    squad.id(),
                    decision,
                    post,
                    stance,
                    target,
                    SquadStance.hold(stance),
                    potion,
                    reason(decision, stance, cluster)));
        }
        CombatStance squadStance = representative(squad, stances);
        return new SquadOrder(
                squad.id(),
                decision,
                post,
                SquadPlacement.bearing(center, cluster.centroid()),
                Optional.empty(),
                squadStance,
                stances,
                potions,
                List.of(),
                squad.members().size(),
                targets,
                reason(decision, squadStance, cluster));
    }

    static SquadOrder reinforce(
            Point center,
            DefenseSquad squad,
            ThreatCluster cluster,
            Point post,
            Optional<String> local,
            DefenseProfile profile,
            DefenseWeights weights,
            List<MemberOrder> members) {
        Map<UUID, CombatStance> stances = new LinkedHashMap<>();
        Map<UUID, DefensePotion> potions = new LinkedHashMap<>();
        DefenseSquad target = new DefenseSquad("цель", squad.members());
        int targets = 0;
        for (DefenseMember member : squad.members()) {
            CombatStance stance = SquadStance.of(member, target, cluster, profile);
            Optional<EnemyFact> enemy = SquadStance.best(member, target, cluster, profile);
            Optional<DefensePotion> potion = SquadStance.potion(member, stance, enemy, profile, weights);
            stances.put(member.id(), stance);
            potion.ifPresent(value -> potions.put(member.id(), value));
            if (enemy.isPresent()) {
                targets++;
            }
            members.add(new MemberOrder(
                    member.id(),
                    squad.id(),
                    SquadDecision.REINFORCE,
                    post,
                    stance,
                    enemy,
                    SquadStance.hold(stance),
                    potion,
                    "отряд идёт на помощь: угроза сильнее местной ёмкости"));
        }
        CombatStance squadStance = representative(squad, stances);
        return new SquadOrder(
                squad.id(),
                SquadDecision.REINFORCE,
                post,
                SquadPlacement.bearing(center, cluster.centroid()),
                local,
                squadStance,
                stances,
                potions,
                List.of(),
                squad.members().size(),
                targets,
                "отряд идёт на помощь: угроза сильнее местной ёмкости");
    }

    static TeamDefensePlan spread(
            Point center,
            DefenseSquad squad,
            List<ThreatCluster> clusters,
            DefenseProfile profile,
            DefenseWeights weights) {
        Map<UUID, CombatStance> stances = new LinkedHashMap<>();
        Map<UUID, DefensePotion> potions = new LinkedHashMap<>();
        List<MemberOrder> members = new ArrayList<>();
        int targets = 0;
        for (int index = 0; index < squad.members().size(); index++) {
            DefenseMember member = squad.members().get(index);
            ThreatCluster cluster = clusters.get(index % clusters.size());
            Point post = SquadPlacement.toward(center, cluster.centroid(), 3);
            CombatStance stance = SquadStance.of(member, squad, cluster, profile);
            Optional<EnemyFact> target = SquadStance.best(member, squad, cluster, profile);
            Optional<DefensePotion> potion = SquadStance.potion(member, stance, target, profile, weights);
            stances.put(member.id(), stance);
            potion.ifPresent(value -> potions.put(member.id(), value));
            if (target.isPresent()) {
                targets++;
            }
            members.add(new MemberOrder(
                    member.id(),
                    squad.id(),
                    SquadDecision.SPREAD,
                    post,
                    stance,
                    target,
                    SquadStance.hold(stance),
                    potion,
                    "отряд сбежался в одну точку: духи расходятся по угрожаемым секторам"));
        }
        List<Double> sectors = clusters.stream().map(ThreatCluster::bearing).toList();
        CombatStance squadStance = representative(squad, stances);
        SquadOrder order = new SquadOrder(
                squad.id(),
                SquadDecision.SPREAD,
                squad.center(),
                clusters.get(0).bearing(),
                Optional.empty(),
                squadStance,
                stances,
                potions,
                sectors,
                squad.members().size(),
                targets,
                "отряд сбежался в одну точку: духи расходятся по угрожаемым секторам");
        return new TeamDefensePlan(center, List.of(squad), List.of(order), List.copyOf(members));
    }

    private static CombatStance memberStance(
            DefenseMember member,
            DefenseSquad squad,
            ThreatCluster cluster,
            SquadDecision decision,
            DefenseProfile profile) {
        if (decision == SquadDecision.RETREAT) {
            return CombatStance.WITHDRAW;
        }
        if (decision == SquadDecision.EAT) {
            return CombatStance.ENGAGE;
        }
        return SquadStance.of(member, squad, cluster, profile);
    }

    private static CombatStance representative(DefenseSquad squad, Map<UUID, CombatStance> stances) {
        DefenseMember best = squad.members().stream()
                .max(Comparator.comparingDouble(DefenseMember::frontRank)
                        .thenComparing(member -> member.id().toString()))
                .orElseThrow();
        return stances.getOrDefault(best.id(), CombatStance.ENGAGE);
    }

    private static Map<UUID, CombatStance> stances(DefenseSquad squad, CombatStance stance) {
        Map<UUID, CombatStance> stances = new LinkedHashMap<>();
        for (DefenseMember member : squad.members()) {
            stances.put(member.id(), stance);
        }
        return stances;
    }

    private static double share(DefenseSquad squad, Predicate<DefenseMember> match) {
        return squad.members().stream().filter(match).count()
                / (double) squad.members().size();
    }

    private static String reason(SquadDecision decision, CombatStance stance, ThreatCluster cluster) {
        return decision.name().toLowerCase(Locale.ROOT) + " (" + stance + "): сектор " + cluster.count()
                + " мобов, вес " + Math.round(cluster.weight() * 100) / 100.0;
    }
}
