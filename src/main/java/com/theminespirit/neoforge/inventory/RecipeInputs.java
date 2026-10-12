package com.theminespirit.neoforge.inventory;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

final class RecipeInputs {
    private RecipeInputs() {
    }

    static ItemStack resultOf(Recipe<?> recipe, HolderLookup.Provider registries) {
        return recipe.getResultItem(registries);
    }

    static List<String> inputsOf(Recipe<?> recipe) {
        List<String> ids = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            for (ItemStack option : ingredient.getItems()) {
                if (!option.isEmpty()) {
                    String id = BuiltInRegistries.ITEM.getKey(option.getItem()).toString();
                    if (!ids.contains(id)) {
                        ids.add(id);
                    }
                }
            }
        }
        return ids;
    }
}
