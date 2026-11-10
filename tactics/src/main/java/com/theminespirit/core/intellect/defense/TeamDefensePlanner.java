package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.EnemyProfiles;
import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TeamDefensePlanner {
    private TeamDefensePlanner() {}

    public static TeamDefensePlan plan(
            Point center,
            List<DefenseMember> defenders,
            List<EnemyFact> enemies,
            EnemyProfiles profiles,
            DefenseProfile profile,
            DefenseWeights weights) {
        require(center, defenders, enemies, profiles, profile, weights);
        List<DefenseSquad> squads = SquadClusters.group(defenders, weights.squadRadius());
        if (squads.isEmpty()) {
            return new TeamDefensePlan(center, List.of(), List.of(), List.of());
        }
        if (enemies.isEmpty()) {
            return stay(center, squads, weights);
        }
        List<ThreatCluster> clusters = ThreatClusters.group(enemies, center, profiles, profile, weights);
        List<List<DefenseSquad>> locals = localSquads(squads, clusters, weights.localRadius());
        if (spread(squads, clusters, locals)) {
            return SquadOrders.spread(center, squads.get(0), clusters, profile, weights);
        }
        Map<String, ThreatCluster> localOf = new HashMap<>();
        for (int index = 0; index < clusters.size(); index++) {
            for (DefenseSquad squad : locals.get(index)) {
                localOf.put(squad.id(), clusters.get(index));
            }
        }
        Map<String, Integer> helperOf = SquadCapacity.help(squads, clusters, locals, localOf, weights);
        Map<String, Point> posts = SquadPlacement.posts(squads, clusters, helperOf, locals, center);
        List<SquadOrder> orders = new ArrayList<>(squads.size());
        List<MemberOrder> members = new ArrayList<>(defenders.size());
        for (DefenseSquad squad : squads) {
            ThreatCluster cluster = localOf.get(squad.id());
            Integer helpIndex = helperOf.get(squad.id());
            if (cluster != null) {
                orders.add(SquadOrders.engage(center, squad, cluster, profile, weights, members));
            } else if (helpIndex != null) {
                Point post = posts.get(squad.id());
                if (post == null) {
                    throw new IllegalStateException(
                            "отряду " + squad.id() + " не назначен пост помощи: таблица постов разошлась с отрядами");
                }
                orders.add(SquadOrders.reinforce(
                        center,
                        squad,
                        clusters.get(helpIndex),
                        post,
                        firstLocalId(locals.get(helpIndex)),
                        profile,
                        weights,
                        members));
            } else {
                orders.add(SquadOrders.stay(center, squad, members, weights));
            }
        }
        return new TeamDefensePlan(center, squads, List.copyOf(orders), List.copyOf(members));
    }

    private static TeamDefensePlan stay(Point center, List<DefenseSquad> squads, DefenseWeights weights) {
        List<SquadOrder> orders = new ArrayList<>(squads.size());
        List<MemberOrder> members = new ArrayList<>();
        for (DefenseSquad squad : squads) {
            orders.add(SquadOrders.stay(center, squad, members, weights));
        }
        return new TeamDefensePlan(center, squads, List.copyOf(orders), List.copyOf(members));
    }

    private static List<List<DefenseSquad>> localSquads(
            List<DefenseSquad> squads, List<ThreatCluster> clusters, double radius) {
        List<List<DefenseSquad>> locals = new ArrayList<>(clusters.size());
        for (int index = 0; index < clusters.size(); index++) {
            locals.add(new ArrayList<>());
        }
        for (DefenseSquad squad : squads) {
            int best = -1;
            double bestDistance = Double.MAX_VALUE;
            for (int index = 0; index < clusters.size(); index++) {
                double distance = squad.center().distanceTo(clusters.get(index).centroid());
                if (distance <= radius && distance < bestDistance) {
                    best = index;
                    bestDistance = distance;
                }
            }
            if (best >= 0) {
                locals.get(best).add(squad);
            }
        }
        return locals;
    }

    private static boolean spread(
            List<DefenseSquad> squads, List<ThreatCluster> clusters, List<List<DefenseSquad>> locals) {
        if (squads.size() != 1 || clusters.size() < 2) {
            return false;
        }
        int uncovered = 0;
        for (List<DefenseSquad> local : locals) {
            if (local.isEmpty()) {
                uncovered++;
            }
        }
        return uncovered >= 2;
    }

    private static Optional<String> firstLocalId(List<DefenseSquad> locals) {
        return locals.stream().map(DefenseSquad::id).sorted().findFirst();
    }

    private static void require(
            Point center,
            List<DefenseMember> defenders,
            List<EnemyFact> enemies,
            EnemyProfiles profiles,
            DefenseProfile profile,
            DefenseWeights weights) {
        if (center == null) {
            throw new IllegalArgumentException("center не может быть null: без защищаемой точки нет секторов");
        }
        if (defenders == null) {
            throw new IllegalArgumentException("defenders не может быть null: нет духов — пустой список");
        }
        if (enemies == null) {
            throw new IllegalArgumentException("enemies не может быть null: нет врагов — пустой список");
        }
        if (profiles == null) {
            throw new IllegalArgumentException("profiles не может быть null: сила врага берётся из enemies.json");
        }
        if (profile == null) {
            throw new IllegalArgumentException("profile не может быть null: класс цели берётся из priorities.json");
        }
        if (weights == null) {
            throw new IllegalArgumentException("weights не может быть null: без весов ёмкость не посчитать");
        }
        if (defenders.stream().anyMatch(member -> member == null)) {
            throw new IllegalArgumentException("defenders содержит null: дух без снимка приказ не получит");
        }
        if (enemies.stream().anyMatch(enemy -> enemy == null)) {
            throw new IllegalArgumentException("enemies содержит null: враг без факта в угрозу не входит");
        }
    }
}
