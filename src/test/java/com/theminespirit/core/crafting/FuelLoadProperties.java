package com.theminespirit.core.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.crafting.FuelLoad.Portion;
import com.theminespirit.core.planner.FuelPlanner;
import com.theminespirit.core.planner.FuelPlanner.FuelRequirement;
import com.theminespirit.core.planner.FuelTable;
import com.theminespirit.core.planner.ResourceKnowledge;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

class FuelLoadProperties {
    private static final int COOK = 200;
    private static final ResourceKnowledge KNOWLEDGE = new FuelKnowledge();

    @Property
    void choose_AfterResolverPlan_BurnsFuelChosenByResolver(
            @ForAll @Size(20) List<@IntRange(min = 0, max = 9) Integer> stock,
            @ForAll @IntRange(min = 1, max = 16) int count) {
        List<String> fuels = FuelTable.preferenceOrder();
        Map<String, Integer> inventory = new HashMap<>();
        for (int i = 0; i < fuels.size(); i++) {
            inventory.put(fuels.get(i), stock.get(i));
        }
        Map<String, Integer> remaining = new HashMap<>(inventory);

        Optional<FuelRequirement> gathered =
                FuelPlanner.select("iron_ingot", count, KNOWLEDGE, new HashSet<>(), remaining);
        String plannedFuel;
        if (gathered.isPresent()) {
            plannedFuel = gathered.get().fuel();
            inventory.merge(plannedFuel, gathered.get().count(), Integer::sum);
        } else {
            plannedFuel = fuels.stream()
                    .filter(fuel -> remaining.get(fuel) < inventory.get(fuel))
                    .findFirst()
                    .orElseThrow();
        }

        List<Portion> load = FuelLoad.choose(count, COOK, inventory, KNOWLEDGE);

        assertEquals(
                1,
                load.size(),
                "Когда резолвер выбрал одно топливо, скрипт обязан жечь одно и то же топливо. Порции: " + load);
        assertEquals(
                plannedFuel,
                load.get(0).fuel(),
                "Скрипт smelt обязан жечь топливо, которое выбрал резолвер: иначе добытое под план топливо "
                        + "остаётся лежать, а горит другое. Запас: " + stock + ", предметов: " + count);
        assertEquals(
                FuelTable.fuelNeeded(count, FuelTable.burnTicks(plannedFuel)),
                load.get(0).count(),
                "Количество топлива обязано быть ровно расчётным: ceil(count × 200 / горение)");
    }

    @Property
    void choose_AnyInventory_NeverTakesMoreThanInventoryHas(
            @ForAll @Size(20) List<@IntRange(min = 0, max = 9) Integer> stock,
            @ForAll @IntRange(min = 1, max = 64) int count) {
        List<String> fuels = FuelTable.preferenceOrder();
        Map<String, Integer> inventory = new HashMap<>();
        for (int i = 0; i < fuels.size(); i++) {
            inventory.put(fuels.get(i), stock.get(i));
        }

        for (Portion portion : FuelLoad.choose(count, COOK, inventory, KNOWLEDGE)) {
            assertTrue(
                    portion.count() <= inventory.get(portion.fuel()),
                    "Порция " + portion + " больше запаса " + inventory.get(portion.fuel())
                            + ": дух не может положить в печь то, чего нет. Запас: " + stock);
        }
    }
}
