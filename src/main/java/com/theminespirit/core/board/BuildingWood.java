package com.theminespirit.core.board;

import com.theminespirit.core.building.WoodSpecies;
import java.util.Map;
import java.util.Objects;

public record BuildingWood(String species, String reason) {
    private static final String NAMESPACE = "minecraft:";
    private static final int PLANKS_PER_LOG = 4;

    public BuildingWood {
        WoodSpecies.check(species);
        Objects.requireNonNull(reason, "Причина выбора породы не может быть null: она пишется в журнал");
    }

    public static BuildingWood choose(
            Map<String, Integer> stock, Map<String, Integer> nearby, String biome, WoodRules rules) {
        Objects.requireNonNull(stock, "Запас не может быть null: пустой задают пустой картой");
        Objects.requireNonNull(nearby, "Деревья вокруг не могут быть null: пустые задают пустой картой");
        Objects.requireNonNull(biome, "Биом не может быть null: он решает породу последним");
        String byStock = best(stock, true);
        int planks = planks(stock, byStock, true);
        if (planks >= rules.stockPlanks()) {
            return new BuildingWood(byStock, "запас " + planks + " досок с брёвнами");
        }
        String byTrees = best(nearby, false);
        int logs = planks(nearby, byTrees, false);
        if (logs > 0) {
            return new BuildingWood(byTrees, "деревья вокруг: " + logs + " брёвен, запас " + planks + " досок");
        }
        return new BuildingWood(WoodSpecies.ofBiome(biome), "биом " + biome + ": ни запаса, ни деревьев вокруг");
    }

    public static BuildingWood fixed(Map<String, Integer> standing, BuildingWood fresh) {
        Objects.requireNonNull(standing, "Стоящие блоки не могут быть null: по ним видна порода постройки");
        Objects.requireNonNull(fresh, "Свежий выбор не может быть null: без него нечего закреплять");
        String byStanding = bestStanding(standing, fresh.species());
        int count = standing.getOrDefault(byStanding, 0);
        if (count == 0) {
            return fresh;
        }
        return new BuildingWood(
                byStanding, "постройка уже стоит: порода закреплена, " + count + " блоков " + byStanding);
    }

    private static String bestStanding(Map<String, Integer> standing, String fresh) {
        String best = fresh;
        int bestCount = standing.getOrDefault(fresh, 0);
        for (String species : WoodSpecies.ALL) {
            int count = standing.getOrDefault(species, 0);
            if (count > bestCount) {
                best = species;
                bestCount = count;
            }
        }
        return best;
    }

    private static String best(Map<String, Integer> items, boolean withPlanks) {
        String best = WoodSpecies.OAK;
        int bestCount = planks(items, best, withPlanks);
        for (String species : WoodSpecies.ALL) {
            int count = planks(items, species, withPlanks);
            if (count > bestCount) {
                best = species;
                bestCount = count;
            }
        }
        return best;
    }

    private static int planks(Map<String, Integer> items, String species, boolean withPlanks) {
        int logs = count(items, species + "_log");
        if (!withPlanks) {
            return logs;
        }
        return logs * PLANKS_PER_LOG + count(items, species + "_planks");
    }

    private static int count(Map<String, Integer> items, String item) {
        return items.getOrDefault(item, 0) + items.getOrDefault(NAMESPACE + item, 0);
    }
}
