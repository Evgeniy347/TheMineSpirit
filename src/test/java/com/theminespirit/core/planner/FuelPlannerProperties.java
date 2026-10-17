package com.theminespirit.core.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

class FuelPlannerProperties {

    @Property
    void select_RandomCandidateModes_ChoosesFirstObtainableNotOnPathWithMinimalAmount(
            @ForAll @Size(min = 1, max = 6) List<@IntRange(min = 0, max = 2) Integer> modes,
            @ForAll @IntRange(min = 0, max = 63) int pathMask,
            @ForAll @IntRange(min = 1, max = 64) int count) {
        List<String> candidates = new ArrayList<>();
        Set<String> gatherable = new HashSet<>();
        Map<String, List<Ingredient>> crafts = new LinkedHashMap<>();
        Map<String, Integer> burn = new LinkedHashMap<>();
        for (int index = 0; index < modes.size(); index++) {
            String fuel = "fuel_" + index;
            candidates.add(fuel);
            burn.put(fuel, 100 + index * 100);
            switch (modes.get(index)) {
                case 0 -> gatherable.add(fuel);
                case 1 -> {
                    gatherable.add("ancestor_" + index);
                    crafts.put(fuel, List.of(new Ingredient("ancestor_" + index, 1)));
                }
                default -> {
                }
            }
        }
        FuelKnowledge knowledge = new FuelKnowledge(candidates, gatherable, crafts, burn);
        Set<String> path = new HashSet<>();
        for (int index = 0; index < modes.size(); index++) {
            if ((pathMask & (1 << index)) != 0) {
                path.add("fuel_" + index);
            }
        }
        int expected = -1;
        for (int index = 0; index < modes.size(); index++) {
            if (modes.get(index) != 2 && !path.contains("fuel_" + index)) {
                expected = index;
                break;
            }
        }

        if (expected < 0) {
            assertThrows(UnplannableGoalException.class,
                    () -> FuelPlanner.select("iron_ingot", count, knowledge, path, new HashMap<>()),
                    "Если ни один кандидат не добывается или все на текущей цепочке, выбор топлива обязан "
                            + "отказать: молчаливый пропуск даст план без топлива и холодную печь. Режимы=" + modes
                            + ", путь=" + path);
            return;
        }
        String fuel = "fuel_" + expected;
        int expectedBurn = 100 + expected * 100;
        FuelPlanner.FuelRequirement chosen = FuelPlanner.select("iron_ingot", count, knowledge, path,
                        new HashMap<>())
                .orElseThrow(() -> new AssertionError("Ожидалось топливо '" + fuel + "': режимы=" + modes
                        + ", путь=" + path));

        assertEquals(fuel, chosen.fuel(),
                "Выбор обязан взять первого кандидата, который добывается (цепочкой предков тоже) и не стоит на "
                        + "текущей цепочке резолвера: режимы=" + modes + ", путь=" + path);
        assertEquals(FuelTable.fuelNeeded(count, expectedBurn), chosen.count(),
                "Топлива надо ровно на плавку без излишка: перебор оставит духа с лишним грузом, недобор встанет "
                        + "посреди работы. Топливо=" + fuel + ", количество плавок=" + count);
    }

    private static final class FuelKnowledge implements ResourceKnowledge {
        private final List<String> candidates;
        private final Set<String> gatherable;
        private final Map<String, List<Ingredient>> crafts;
        private final Map<String, Integer> burn;

        private FuelKnowledge(List<String> candidates, Set<String> gatherable,
                Map<String, List<Ingredient>> crafts, Map<String, Integer> burn) {
            this.candidates = List.copyOf(candidates);
            this.gatherable = Set.copyOf(gatherable);
            this.crafts = Map.copyOf(crafts);
            this.burn = Map.copyOf(burn);
        }

        @Override
        public boolean isGatherable(String item) {
            return gatherable.contains(item);
        }

        @Override
        public boolean isCraftable(String item) {
            return crafts.containsKey(item);
        }

        @Override
        public int outputCount(String item) {
            return 1;
        }

        @Override
        public List<Ingredient> ingredientsFor(String item) {
            return crafts.getOrDefault(item, List.of());
        }

        @Override
        public boolean isSmeltable(String item) {
            return false;
        }

        @Override
        public String smeltInputFor(String item) {
            return null;
        }

        @Override
        public int burnTicks(String item) {
            return burn.getOrDefault(item, 0);
        }

        @Override
        public List<String> fuelCandidates() {
            return candidates;
        }
    }
}
