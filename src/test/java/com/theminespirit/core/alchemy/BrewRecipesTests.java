package com.theminespirit.core.alchemy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BrewRecipesTests {

    @Test
    void healing_RecipeChain_FollowsVanillaOrder() {
        List<BrewRecipes.Step> steps = BrewRecipes.steps("potion_healing").orElseThrow();

        assertEquals(
                List.of(BrewRecipes.BASE_INGREDIENT, "glistering_melon_slice"),
                steps.stream().map(BrewRecipes.Step::ingredient).toList(),
                "Лечение варят по ванили: сперва адский нарост делает неловкое зелье, затем сверкающий ломтик "
                        + "арбуза даёт лечение");
        assertEquals(
                List.of(BrewRecipes.AWKWARD, "minecraft:healing"),
                steps.stream().map(BrewRecipes.Step::potion).toList(),
                "Порядок зелий обязан совпадать с ванильным: вода → неловкое → лечение");
    }

    @Test
    void splash_RecipeChain_EndsWithGunpowder() {
        List<BrewRecipes.Step> steps =
                BrewRecipes.steps("splash_potion_healing").orElseThrow();

        assertEquals(
                List.of(BrewRecipes.BASE_INGREDIENT, "glistering_melon_slice", BrewRecipes.SPLASH_INGREDIENT),
                steps.stream().map(BrewRecipes.Step::ingredient).toList(),
                "Взрывное зелье — обычное зелье плюс порох последним шагом");
        assertEquals(
                BrewRecipes.SPLASH_ITEM,
                steps.get(steps.size() - 1).item(),
                "Последний шаг взрывного зелья даёт splash_potion, а не обычное зелье");
    }

    @Test
    void effects_VanillaIngredients_MatchPotionNames() {
        Map<String, String> expected = Map.of(
                "potion_fire_resistance", "magma_cream",
                "potion_regeneration", "ghast_tear",
                "potion_strength", "blaze_powder",
                "potion_swiftness", "sugar",
                "potion_night_vision", "golden_carrot",
                "potion_water_breathing", "pufferfish");

        expected.forEach((product, ingredient) -> assertEquals(
                ingredient,
                BrewRecipes.steps(product).orElseThrow().get(1).ingredient(),
                "Ингредиент зелья " + product + " обязан быть ванильным: " + ingredient));
    }

    @Test
    void needs_OneSplashPotion_CountsBottlesFuel() {
        Map<String, Integer> needs = BrewRecipes.needs("splash_potion_regeneration", 1);

        assertEquals(
                Map.of(
                        "glass_bottle", 1,
                        "nether_wart", 1,
                        "ghast_tear", 1,
                        "gunpowder", 1,
                        "blaze_powder", 3),
                needs,
                "Взрывное зелье из одной бутылки: три шага варки, поэтому три топлива, порох и ингредиент");
        assertEquals(
                3,
                BrewRecipes.fuel("splash_potion_regeneration", 1),
                "Топливо взрывного зелья — по одному огненному порошку на шаг стойки");
    }

    @Test
    void batches_FourPotions_SplitIntoTwoRuns() {
        assertEquals(1, BrewRecipes.batches(3), "Стойка варит до трёх бутылок за один цикл");
        assertEquals(2, BrewRecipes.batches(4), "Четвёртая бутылка требует второго цикла");
        assertEquals(4, BrewRecipes.fuel("potion_healing", 4), "Два цикла лечения по два шага — четыре топлива");
    }

    @Test
    void unknownProduct_NoRecipe_IsRejected() {
        assertTrue(BrewRecipes.steps("potion_unknown").isEmpty(), "Неизвестное зелье не должно получить рецепт");
        assertFalse(BrewRecipes.splashing("potion_healing"), "Обычное зелье не взрывное");
        assertTrue(BrewRecipes.splashing("splash_potion_healing"), "Взрывное зелье распознаётся по префиксу");
        assertThrows(
                IllegalArgumentException.class,
                () -> BrewRecipes.needs("potion_unknown", 1),
                "Планировщик обязан отказать, если для зелья нет рецепта");
    }
}
