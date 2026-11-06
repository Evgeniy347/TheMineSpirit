package com.theminespirit.core.crafting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecipeReachabilityTests {
    @Test
    void canObtain_BranchingRecipeChain_StaysCheap() {
        RecipeIndex index = RecipeIndex.of(chain(7, 4));

        RecipeReachability reach = new RecipeReachability(index, "b7"::equals);

        assertTrue(reach.canObtain("b0"), "Цепочка из крафтов до добываемого b7 обязана быть достижимой");
        assertTrue(
                index.lookupProbes() < 200,
                "Повторный разбор одной и той же ветки рецептов обязан кэшироваться: иначе живой планировщик "
                        + "зависает на кирпичах и ступенях (observe18 B5), а поисков было " + index.lookupProbes());
    }

    @Test
    void canObtain_RecipeCycle_ReturnsFalseWithoutHanging() {
        RecipeIndex index =
                RecipeIndex.of(List.of(RecipeSpec.shaped("cycle", "a", 1, 1, 1, List.of(IngredientSpec.of("a")))));

        assertFalse(
                new RecipeReachability(index, item -> false).canObtain("a"),
                "Рецепт, который делает предмет из самого себя, не делает его достижимым");
    }

    private static List<RecipeSpec> chain(int depth, int branches) {
        List<RecipeSpec> recipes = new ArrayList<>();
        for (int level = 0; level < depth; level++) {
            List<IngredientSpec> ingredients = new ArrayList<>();
            for (int branch = 0; branch < branches; branch++) {
                ingredients.add(IngredientSpec.of("b" + (level + 1)));
            }
            recipes.add(RecipeSpec.shaped("r" + level, "b" + level, 1, 2, 2, ingredients));
        }
        return recipes;
    }
}
