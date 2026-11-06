package com.theminespirit.core.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

class RecipeChooserProperties {
    private static final int ITEMS = 4;

    @Property
    void pick_ShuffledCandidates_ChoosesSameRecipe(
            @ForAll @Size(min = 1, max = 8) List<@IntRange(min = 0, max = 999) Integer> seeds,
            @ForAll @Size(ITEMS) List<@IntRange(min = 0, max = 3) Integer> stock,
            @ForAll long shuffleSeed) {
        List<RecipeSpec> candidates = new ArrayList<>();
        for (int i = 0; i < seeds.size(); i++) {
            candidates.add(recipe(i, seeds.get(i)));
        }
        List<RecipeSpec> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, new Random(shuffleSeed));
        Map<String, Integer> available = new HashMap<>();
        for (int item = 0; item < ITEMS; item++) {
            available.put("item_" + item, stock.get(item));
        }

        Optional<RecipeSpec> straight = RecipeChooser.pick(candidates, available);
        Optional<RecipeSpec> mixed = RecipeChooser.pick(shuffled, available);

        assertEquals(
                straight,
                mixed,
                "Выбор рецепта не должен зависеть от порядка кандидатов: порядок рецептов в датапаках случаен, "
                        + "а духи обязаны выбирать одинаково. Рецептов: " + candidates.size() + ", наличие: " + stock);
    }

    private static RecipeSpec recipe(int index, int seed) {
        List<IngredientSpec> ingredients = new ArrayList<>();
        int slots = 1 + seed % 3;
        for (int slot = 0; slot < slots; slot++) {
            ingredients.add(IngredientSpec.of("item_" + (seed / (slot + 1) % ITEMS)));
        }
        return RecipeSpec.shapeless("recipe_" + index, "result", 1 + seed % 4, ingredients);
    }
}
