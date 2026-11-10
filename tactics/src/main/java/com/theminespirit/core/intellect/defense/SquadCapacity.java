package com.theminespirit.core.intellect.defense;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SquadCapacity {
    private SquadCapacity() {}

    static double of(DefenseSquad squad, DefenseWeights weights) {
        double capacity = 0;
        for (DefenseMember member : squad.members()) {
            capacity += of(member, weights);
        }
        return capacity;
    }

    static double of(DefenseMember member, DefenseWeights weights) {
        double health = weights.healthFloor() + weights.healthShare() * member.hpFraction();
        double armor =
                weights.armorFloor() + (1 - weights.armorFloor()) * Math.min(1, member.armor() / weights.armorFull());
        double weapon = weights.weaponFloor()
                + (1 - weights.weaponFloor()) * Math.min(1, member.meleeDps() / weights.weaponFull());
        double food = member.food() >= weights.foodFull() ? 1 : weights.foodPenalty();
        double strength = member.has(DefensePotion.STRENGTH) ? 1 + weights.strengthBonus() : 1;
        return weights.baseCapacity() * health * armor * weapon * food * strength;
    }

    static Map<String, Integer> help(
            List<DefenseSquad> squads,
            List<ThreatCluster> clusters,
            List<List<DefenseSquad>> locals,
            Map<String, ThreatCluster> localOf,
            DefenseWeights weights) {
        Map<String, Integer> helperOf = new LinkedHashMap<>();
        double[] deficits = new double[clusters.size()];
        for (int index = 0; index < clusters.size(); index++) {
            deficits[index] = deficit(clusters.get(index), locals.get(index), weights);
        }
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < clusters.size(); index++) {
            order.add(index);
        }
        order.sort(Comparator.comparingDouble((Integer index) -> deficits[index])
                .reversed()
                .thenComparingInt(index -> index));
        for (int index : order) {
            if (deficits[index] <= 0) {
                continue;
            }
            int helpers = 0;
            List<DefenseSquad> candidates = squads.stream()
                    .filter(squad -> !localOf.containsKey(squad.id()))
                    .filter(squad -> !helperOf.containsKey(squad.id()))
                    .sorted(Comparator.comparingDouble((DefenseSquad squad) -> squad.center()
                                    .distanceTo(clusters.get(index).centroid()))
                            .thenComparing(DefenseSquad::id))
                    .toList();
            double left = deficits[index];
            for (DefenseSquad candidate : candidates) {
                if (left <= 0 || helpers >= weights.maxHelpersPerThreat()) {
                    break;
                }
                helperOf.put(candidate.id(), index);
                helpers += candidate.members().size();
                left -= of(candidate, weights);
            }
        }
        return helperOf;
    }

    private static double deficit(ThreatCluster cluster, List<DefenseSquad> locals, DefenseWeights weights) {
        double capacity = 0;
        for (DefenseSquad squad : locals) {
            capacity += of(squad, weights);
        }
        return Math.max(0, cluster.weight() - capacity);
    }
}
