package com.theminespirit.neoforge.inventory;

import com.theminespirit.core.planner.Ingredient;
import com.theminespirit.core.planner.ResourceKnowledge;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;

final class RecipeKnowledge implements ResourceKnowledge {
    private final Map<String, List<Ingredient>> inputs = new HashMap<>();
    private final Map<String, String> smeltInputs = new HashMap<>();
    private final Map<String, Integer> outputs = new HashMap<>();

    static RecipeKnowledge of(Level level) {
        RecipeKnowledge knowledge = new RecipeKnowledge();
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            ItemStack result = RecipeInputs.resultOf(holder.value(), level.registryAccess());
            if (result.isEmpty()) {
                continue;
            }
            knowledge.add(BuiltInRegistries.ITEM.getKey(result.getItem()).toString(), result.getCount(),
                    RecipeInputs.inputsOf(holder.value()), holder.value() instanceof AbstractCookingRecipe);
        }
        return knowledge;
    }

    @Override
    public boolean isGatherable(String item) {
        return false;
    }

    @Override
    public boolean isCraftable(String item) {
        return inputs.containsKey(item);
    }

    @Override
    public int outputCount(String item) {
        return outputs.getOrDefault(item, 1);
    }

    @Override
    public List<Ingredient> ingredientsFor(String item) {
        return inputs.getOrDefault(item, List.of());
    }

    @Override
    public boolean isSmeltable(String item) {
        return smeltInputs.containsKey(item);
    }

    @Override
    public String smeltInputFor(String item) {
        return smeltInputs.get(item);
    }

    private void add(String resultId, int resultCount, List<String> inputIds, boolean cooking) {
        if (inputIds.isEmpty()) {
            return;
        }
        List<Ingredient> found = new ArrayList<>(inputIds.size());
        for (String id : inputIds) {
            found.add(new Ingredient(id, 1));
        }
        inputs.computeIfAbsent(resultId, key -> new ArrayList<>()).addAll(found);
        outputs.putIfAbsent(resultId, resultCount);
        if (cooking) {
            smeltInputs.putIfAbsent(resultId, inputIds.get(0));
        }
    }
}
