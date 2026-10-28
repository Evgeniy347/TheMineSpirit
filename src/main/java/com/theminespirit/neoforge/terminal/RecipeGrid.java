package com.theminespirit.neoforge.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;

final class RecipeGrid {
    static final int SIDE = 3;

    private RecipeGrid() {}

    static Optional<RecipeHolder<CraftingRecipe>> crafting(ServerLevel level, String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null) {
            return Optional.empty();
        }
        return level.getRecipeManager()
                .byKey(key)
                .filter(holder -> holder.value() instanceof CraftingRecipe)
                .map(holder -> new RecipeHolder<>(holder.id(), (CraftingRecipe) holder.value()));
    }

    static List<Optional<Ingredient>> layout(CraftingRecipe recipe) {
        List<Optional<Ingredient>> slots = new ArrayList<>(SIDE * SIDE);
        for (int slot = 0; slot < SIDE * SIDE; slot++) {
            slots.add(Optional.empty());
        }
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        if (recipe instanceof ShapedRecipe shaped) {
            for (int y = 0; y < shaped.getHeight() && y < SIDE; y++) {
                for (int x = 0; x < shaped.getWidth() && x < SIDE; x++) {
                    Ingredient ingredient = ingredients.get(y * shaped.getWidth() + x);
                    slots.set(y * SIDE + x, ingredient.isEmpty() ? Optional.empty() : Optional.of(ingredient));
                }
            }
            return slots;
        }
        int next = 0;
        for (Ingredient ingredient : ingredients) {
            if (!ingredient.isEmpty() && next < SIDE * SIDE) {
                slots.set(next++, Optional.of(ingredient));
            }
        }
        return slots;
    }

    static CraftingInput input(CraftingRecipe recipe, List<ItemStack> filled) {
        if (recipe instanceof ShapedRecipe shaped) {
            List<ItemStack> cells = new ArrayList<>(shaped.getWidth() * shaped.getHeight());
            int next = 0;
            for (Ingredient ingredient : recipe.getIngredients()) {
                cells.add(ingredient.isEmpty() || next >= filled.size() ? ItemStack.EMPTY : filled.get(next++));
            }
            return CraftingInput.of(shaped.getWidth(), shaped.getHeight(), cells);
        }
        return CraftingInput.of(filled.size(), 1, filled);
    }
}
