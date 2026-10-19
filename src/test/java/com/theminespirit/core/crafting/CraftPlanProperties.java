package com.theminespirit.core.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

class CraftPlanProperties {
    private static final int ITEMS = 3;

    @Property
    void of_SingleAlternativeRecipe_FeasibleExactlyWhenStockCoversCrafts(
            @ForAll @Size(min = 1, max = 5) List<@IntRange(min = 0, max = ITEMS - 1) Integer> slotItems,
            @ForAll @IntRange(min = 1, max = 20) int count,
            @ForAll @IntRange(min = 1, max = 4) int outputCount,
            @ForAll @Size(ITEMS) List<@IntRange(min = 0, max = 30) Integer> stock) {
        RecipeSpec recipe = recipe(slotItems, outputCount);
        Map<String, Integer> available = available(stock);
        int crafts = (count + outputCount - 1) / outputCount;
        boolean covered = true;
        for (int item = 0; item < ITEMS; item++) {
            int perCraft = occurrences(slotItems, item);
            covered &= perCraft * crafts <= stock.get(item);
        }

        CraftPlan plan = CraftPlan.of(recipe, count, available);

        assertEquals(
                covered,
                plan.missing().isEmpty(),
                "Пустой missing обязан значить 'выполнимо', непустой — 'не хватает': иначе дух крафтит без "
                        + "материалов или отказывает при их наличии. Места: " + slotItems + ", число: " + count
                        + ", выход: " + outputCount + ", наличие: " + stock);
        assertEquals(crafts, plan.crafts(), "Число крафтов обязано быть ceil(count / выход)");
    }

    @Property
    void of_FeasiblePlan_RequiredNeverExceedsAvailable(
            @ForAll @Size(min = 1, max = 5) List<@IntRange(min = 1, max = 7) Integer> masks,
            @ForAll @IntRange(min = 1, max = 12) int count,
            @ForAll @Size(ITEMS) List<@IntRange(min = 0, max = 30) Integer> stock) {
        List<IngredientSpec> slots = new ArrayList<>();
        for (int mask : masks) {
            List<String> alternatives = new ArrayList<>();
            for (int item = 0; item < ITEMS; item++) {
                if ((mask >> item & 1) == 1) {
                    alternatives.add("item_" + item);
                }
            }
            slots.add(new IngredientSpec(alternatives));
        }
        RecipeSpec recipe = RecipeSpec.shapeless("r", "result", 1, slots);
        Map<String, Integer> available = available(stock);

        CraftPlan plan = CraftPlan.of(recipe, count, available);

        int total =
                plan.required().values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(
                plan.crafts() * slots.size(),
                total,
                "Суммарные расходы обязаны равняться числу крафтов на число мест рецепта");
        if (plan.feasible()) {
            plan.required()
                    .forEach((item, need) -> assertTrue(
                            need <= available.getOrDefault(item, 0),
                            "Выполнимый план расходует " + item + " " + need + " при наличии "
                                    + available.getOrDefault(item, 0) + ": дух не может отдать то, чего нет"));
        }
    }

    private static int occurrences(List<Integer> slotItems, int item) {
        int perCraft = 0;
        for (int slotItem : slotItems) {
            if (slotItem == item) {
                perCraft++;
            }
        }
        return perCraft;
    }

    private static RecipeSpec recipe(List<Integer> slotItems, int outputCount) {
        List<IngredientSpec> slots = new ArrayList<>();
        for (int item : slotItems) {
            slots.add(IngredientSpec.of("item_" + item));
        }
        return RecipeSpec.shapeless("r", "result", outputCount, slots);
    }

    private static Map<String, Integer> available(List<Integer> stock) {
        Map<String, Integer> available = new HashMap<>();
        for (int item = 0; item < stock.size(); item++) {
            available.put("item_" + item, stock.get(item));
        }
        return available;
    }
}
