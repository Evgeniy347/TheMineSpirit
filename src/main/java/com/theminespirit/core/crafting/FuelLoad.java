package com.theminespirit.core.crafting;

import com.theminespirit.core.planner.FuelTable;
import com.theminespirit.core.planner.ResourceKnowledge;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FuelLoad {
    public record Portion(String fuel, int count) {
        public Portion {
            if (fuel == null || fuel.isBlank()) {
                throw new IllegalArgumentException("Топливо порции пусто: без имени дух не знает, что класть в печь");
            }
            if (count <= 0) {
                throw new IllegalArgumentException(
                        "Порция топлива должна быть больше 0, а получено " + count + ": пустая порция — мёртвый шаг");
            }
        }
    }

    private FuelLoad() {}

    public static List<Portion> choose(
            int count, int cookTicks, Map<String, Integer> inventory, ResourceKnowledge knowledge) {
        Objects.requireNonNull(inventory, "inventory не может быть null: без инвентаря не выбрать топливо");
        Objects.requireNonNull(knowledge, "knowledge не может быть null: без знаний неизвестно время горения");
        List<String> candidates = knowledge.fuelCandidates();
        for (String fuel : candidates) {
            int burn = knowledge.burnTicks(fuel);
            if (burn > 0 && inventory.getOrDefault(fuel, 0) >= FuelTable.fuelNeeded(count, burn, cookTicks)) {
                return List.of(new Portion(fuel, FuelTable.fuelNeeded(count, burn, cookTicks)));
            }
        }
        return cover(count, cookTicks, inventory, knowledge, candidates);
    }

    private static List<Portion> cover(
            int count,
            int cookTicks,
            Map<String, Integer> inventory,
            ResourceKnowledge knowledge,
            List<String> candidates) {
        long missingTicks = (long) count * cookTicks;
        List<Portion> portions = new ArrayList<>();
        for (String fuel : candidates) {
            int burn = knowledge.burnTicks(fuel);
            int have = inventory.getOrDefault(fuel, 0);
            if (missingTicks <= 0) {
                break;
            }
            if (burn > 0 && have > 0) {
                int take = (int) Math.min(have, (missingTicks + burn - 1) / burn);
                portions.add(new Portion(fuel, take));
                missingTicks -= (long) take * burn;
            }
        }
        return List.copyOf(portions);
    }
}
