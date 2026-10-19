package com.theminespirit.core.planner;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

public final class FuelPlanner {
    private FuelPlanner() {}

    public static Optional<FuelRequirement> select(
            String smelted, int count, ResourceKnowledge knowledge, Set<String> path, Map<String, Integer> remaining) {
        List<String> candidates = fuelCandidates(knowledge, smelted);
        String partialFuel = null;
        int partialHave = 0;
        int partialBurn = 0;
        String freshFuel = null;
        int freshBurn = 0;
        for (String fuel : candidates) {
            if (path.contains(fuel)) {
                continue;
            }
            int burn = knowledge.burnTicks(fuel);
            if (burn <= 0) {
                continue;
            }
            int need = FuelTable.fuelNeeded(count, burn);
            int have = remaining.getOrDefault(fuel, 0);
            if (have >= need) {
                remaining.put(fuel, have - need);
                return Optional.empty();
            }
            if (partialFuel == null && have > 0 && canProvide(fuel, knowledge, new HashSet<>())) {
                partialFuel = fuel;
                partialHave = have;
                partialBurn = burn;
            }
            if (freshFuel == null && canProvide(fuel, knowledge, new HashSet<>())) {
                freshFuel = fuel;
                freshBurn = burn;
            }
        }
        if (partialFuel != null) {
            int need = FuelTable.fuelNeeded(count, partialBurn);
            remaining.put(partialFuel, 0);
            return Optional.of(new FuelRequirement(partialFuel, need - partialHave));
        }
        if (freshFuel != null) {
            return Optional.of(new FuelRequirement(freshFuel, FuelTable.fuelNeeded(count, freshBurn)));
        }
        StringJoiner names = new StringJoiner(", ");
        for (String fuel : candidates) {
            names.add(fuel);
        }
        throw new UnplannableGoalException(
                smelted,
                "Для плавки '" + smelted + "' ×" + count
                        + " нет доступного топлива (" + names
                        + "): добавьте топливо в знания как gatherable, craftable или "
                        + "smeltable либо выдайте духу топливо в инвентарь");
    }

    private static List<String> fuelCandidates(ResourceKnowledge knowledge, String smelted) {
        List<String> candidates = knowledge.fuelCandidates();
        if (candidates == null || candidates.isEmpty()) {
            throw new UnplannableGoalException(
                    smelted,
                    "Для плавки '" + smelted
                            + "' знания не дали ни одного кандидата в топливо: верните порядок предпочтения топлива");
        }
        return candidates;
    }

    private static boolean canProvide(String item, ResourceKnowledge knowledge, Set<String> path) {
        if (!path.add(item)) {
            return false;
        }
        try {
            if (knowledge.isGatherable(item)) {
                return true;
            }
            if (knowledge.isCraftable(item)) {
                return knowledge.ingredientsFor(item).stream()
                        .allMatch(ingredient -> canProvide(ingredient.item(), knowledge, path));
            }
            if (knowledge.isSmeltable(item)) {
                return canProvide(knowledge.smeltInputFor(item), knowledge, path);
            }
            return false;
        } finally {
            path.remove(item);
        }
    }

    public record FuelRequirement(String fuel, int count) {
        public FuelRequirement {
            if (fuel == null || fuel.isBlank()) {
                throw new IllegalArgumentException("Топливо требования пусто: без имени резолвер не добудет топливо");
            }
            if (count <= 0) {
                throw new IllegalArgumentException("Количество топлива должно быть больше 0, а получено " + count
                        + ": требование добыть ноль топлива — мёртвый шаг плана");
            }
        }
    }
}
