package com.theminespirit.neoforge.crafting;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.crafting.IngredientSpec;
import com.theminespirit.core.crafting.RecipeSpec;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import org.slf4j.Logger;

public final class RecipeSource {
    private static final Logger LOGGER = LogUtils.getLogger();

    private RecipeSource() {}

    public static List<RecipeSpec> read(RecipeManager manager, HolderLookup.Provider registries) {
        List<RecipeSpec> specs = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> holder : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
            try {
                crafting(holder, registries).ifPresent(specs::add);
            } catch (IllegalArgumentException e) {
                skipped(holder.id().toString(), e);
            }
        }
        for (RecipeHolder<SmeltingRecipe> holder : manager.getAllRecipesFor(RecipeType.SMELTING)) {
            try {
                smelting(holder, registries).ifPresent(specs::add);
            } catch (IllegalArgumentException e) {
                skipped(holder.id().toString(), e);
            }
        }
        return List.copyOf(specs);
    }

    private static void skipped(String id, IllegalArgumentException e) {
        LOGGER.warn("Рецепт {} пропущен: не укладывается в модель крафта духа ({})", id, e.getMessage());
    }

    private static Optional<RecipeSpec> crafting(
            RecipeHolder<CraftingRecipe> holder, HolderLookup.Provider registries) {
        CraftingRecipe recipe = holder.value();
        ItemStack result = recipe.getResultItem(registries);
        if (recipe.isSpecial() || result.isEmpty()) {
            return Optional.empty();
        }
        List<IngredientSpec> ingredients = ingredients(recipe.getIngredients());
        if (ingredients.isEmpty() || ingredients.size() != countFilled(recipe.getIngredients())) {
            return Optional.empty();
        }
        String id = holder.id().toString();
        String output = ItemIds.of(result.getItem());
        if (recipe instanceof ShapedRecipe shaped) {
            return Optional.of(RecipeSpec.shaped(
                    id, output, result.getCount(), shaped.getWidth(), shaped.getHeight(), ingredients));
        }
        if (recipe instanceof ShapelessRecipe) {
            return Optional.of(RecipeSpec.shapeless(id, output, result.getCount(), ingredients));
        }
        return Optional.empty();
    }

    private static Optional<RecipeSpec> smelting(
            RecipeHolder<SmeltingRecipe> holder, HolderLookup.Provider registries) {
        SmeltingRecipe recipe = holder.value();
        ItemStack result = recipe.getResultItem(registries);
        List<IngredientSpec> ingredients = ingredients(recipe.getIngredients());
        if (result.isEmpty() || ingredients.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(RecipeSpec.smelting(
                holder.id().toString(),
                ItemIds.of(result.getItem()),
                result.getCount(),
                ingredients.get(0),
                recipe.getCookingTime()));
    }

    private static List<IngredientSpec> ingredients(List<Ingredient> source) {
        List<IngredientSpec> specs = new ArrayList<>();
        for (Ingredient ingredient : source) {
            if (!ingredient.isEmpty()) {
                alternatives(ingredient).ifPresent(specs::add);
            }
        }
        return specs;
    }

    private static int countFilled(List<Ingredient> source) {
        return (int) source.stream().filter(ingredient -> !ingredient.isEmpty()).count();
    }

    private static Optional<IngredientSpec> alternatives(Ingredient ingredient) {
        Set<String> ids = new LinkedHashSet<>();
        for (ItemStack stack : ingredient.getItems()) {
            if (!stack.isEmpty()) {
                ids.add(ItemIds.of(stack.getItem()));
            }
        }
        return ids.isEmpty() ? Optional.empty() : Optional.of(new IngredientSpec(List.copyOf(ids)));
    }
}
