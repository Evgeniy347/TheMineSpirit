package com.theminespirit.core.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Size;

class RecipeCuratorProperties {
    private static final int ITEMS = 6;
    private static final int MAX_RECIPES = 14;

    @Property
    void curate_AnyRecipes_IsIdempotent(@ForAll @Size(max = MAX_RECIPES) List<Integer> seeds) {
        List<RecipeSpec> once = RecipeCurator.curate(recipes(seeds));

        assertEquals(
                once,
                RecipeCurator.curate(once),
                "Куратор обязан быть идемпотентным: повторный отбор отфильтрованного списка не вправе убирать "
                        + "ещё рецепты, иначе результат зависит от числа проходов. Вход: " + seeds);
    }

    @Property
    void curate_AnyRecipes_AddsNothingAndKeepsOrder(@ForAll @Size(max = MAX_RECIPES) List<Integer> seeds) {
        List<RecipeSpec> input = recipes(seeds);

        List<RecipeSpec> curated = RecipeCurator.curate(input);

        List<RecipeSpec> expectedOrder = new ArrayList<>(input);
        expectedOrder.retainAll(curated);
        assertEquals(
                expectedOrder,
                curated,
                "Куратор обязан только убирать рецепты, не добавляя и не переставляя: иначе в индекс попадёт "
                        + "рецепт, которого нет на сервере. Вход: " + seeds);
    }

    @Property
    void curate_ShuffledInput_KeepsSameSet(
            @ForAll @Size(max = MAX_RECIPES) List<Integer> seeds, @ForAll long shuffleSeed) {
        List<RecipeSpec> input = recipes(seeds);
        List<RecipeSpec> shuffled = new ArrayList<>(input);
        Collections.shuffle(shuffled, new Random(shuffleSeed));

        Set<RecipeSpec> straight = new HashSet<>(RecipeCurator.curate(input));
        Set<RecipeSpec> mixed = new HashSet<>(RecipeCurator.curate(shuffled));

        assertEquals(
                straight,
                mixed,
                "Порядок рецептов на входе не должен влиять на состав отобранных: порядок загрузки датапаков "
                        + "случаен. Вход: " + seeds + ", прямо: " + straight.size() + ", вперемешку: " + mixed.size());
    }

    private static List<RecipeSpec> recipes(List<Integer> seeds) {
        List<RecipeSpec> recipes = new ArrayList<>();
        for (int i = 0; i < seeds.size(); i++) {
            recipes.add(recipe(i, seeds.get(i)));
        }
        return recipes;
    }

    private static RecipeSpec recipe(int index, int seed) {
        int bits = Math.abs(seed % 100_000);
        String output = item(bits % ITEMS);
        String id = "r" + index;
        int first = (bits / ITEMS) % ITEMS;
        int second = (bits / (ITEMS * ITEMS)) % ITEMS;
        int kind = (bits / (ITEMS * ITEMS * ITEMS)) % 3;
        if (kind == 0) {
            return RecipeSpec.smelting(id, output, 1, IngredientSpec.of(item(first)), 200);
        }
        int outputCount = 1 + (bits / 7) % 9;
        List<IngredientSpec> ingredients = new ArrayList<>();
        ingredients.add(IngredientSpec.of(item(first)));
        if (kind == 2) {
            ingredients.add(
                    first == second ? IngredientSpec.of(item(first)) : IngredientSpec.of(item(second), item(first)));
        }
        return RecipeSpec.shapeless(id, output, outputCount, ingredients);
    }

    private static String item(int index) {
        return "item_" + index;
    }
}
