package com.theminespirit.core.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecipeIndexTests {
    private static final int RECIPES = 5_000;
    private static final int LOOKUPS = 1_000;
    private static final int MAX_BUILD_STEPS = 5_000;
    private static final int MAX_PROBES_PER_LOOKUP = 1;

    private static RecipeSpec craft(String id, String output) {
        return RecipeSpec.shapeless(id, output, 1, List.of(IngredientSpec.of("raw_" + output)));
    }

    @Test
    void craftsFor_SeveralRecipesOfOneItem_ReturnsAllOfThemInInputOrder() {
        RecipeSpec first = craft("stick_a", "stick");
        RecipeSpec second = craft("stick_b", "stick");
        RecipeIndex index = RecipeIndex.of(List.of(first, craft("other", "bowl"), second));

        assertEquals(
                List.of(first, second),
                index.craftsFor("stick"),
                "Индекс обязан отдавать все рецепты предмета: выбор лучшего делает RecipeChooser, иначе у палок "
                        + "остаётся один путь и палки из бамбука недоступны. При смене RecipeIndex.craftsFor "
                        + "проверь строку неизвестного предмета ниже в тесте");
    }

    @Test
    void craftsFor_UnknownItem_ReturnsEmpty() {
        RecipeIndex index = RecipeIndex.of(List.of(craft("stick", "stick")));

        assertEquals(
                List.of(),
                index.craftsFor("bowl"),
                "Для предмета без рецептов индекс обязан вернуть пустой список, а не null: резолвер сверяет по нему "
                        + "isCraftable, иначе null роняет вызов на каждом неизвестном предмете. При смене "
                        + "craftsFor проверь строку нескольких рецептов выше в тесте");
    }

    @Test
    void smeltingFor_SmeltingRecipes_AreSeparateFromCrafting() {
        RecipeSpec smelt = RecipeSpec.smelting("iron_a", "iron_ingot", 1, IngredientSpec.of("raw_iron"), 200);
        RecipeSpec otherSmelt = RecipeSpec.smelting("iron_b", "iron_ingot", 1, IngredientSpec.of("iron_ore"), 200);
        RecipeSpec craftRecipe = craft("iron_block_craft", "iron_ingot");
        RecipeIndex index = RecipeIndex.of(List.of(smelt, craftRecipe, otherSmelt));

        assertEquals(
                List.of(smelt, otherSmelt),
                index.smeltingFor("iron_ingot"),
                "Рецепты плавки обязаны лежать отдельно от крафта: иначе слиток «крафтится» и резолвер не идёт в "
                        + "печь, а руда лежит зря. При смене RecipeIndex.smeltingFor проверь строку крафта ниже в "
                        + "тесте");
        assertEquals(
                List.of(craftRecipe),
                index.craftsFor("iron_ingot"),
                "Крафт предмета не должен смешиваться с рецептами плавки: иначе крафт попадает в список печи и "
                        + "выбор рецепта путается. При смене разделения проверь строку smeltingFor выше в тесте");
    }

    @Test
    void build_ManyRecipes_VisitsEachRecipeOnce() {
        RecipeIndex index = RecipeIndex.of(synthetic());

        assertTrue(
                index.buildSteps() <= MAX_BUILD_STEPS,
                "Построение индекса обязано быть линейным: не больше " + MAX_BUILD_STEPS + " шагов на " + RECIPES
                        + " рецептов, фактически " + index.buildSteps() + ". Квадратичное построение на 1–2 тысячах "
                        + "рецептов игры стоит секунд при каждой перезагрузке датапаков, иначе сервер встаёт на "
                        + "старте. При смене RecipeIndex.of проверь счётчик buildSteps выше в тесте");
    }

    @Test
    void lookup_ManyLookups_CostsOneProbeEach() {
        RecipeIndex index = RecipeIndex.of(synthetic());

        for (int i = 0; i < LOOKUPS; i++) {
            index.craftsFor("item_" + (i * 7 % RECIPES));
        }

        assertTrue(
                index.lookupProbes() <= LOOKUPS * MAX_PROBES_PER_LOOKUP,
                "Поиск рецепта обязан быть O(1): не больше " + MAX_PROBES_PER_LOOKUP + " обращения к карте на запрос, "
                        + "фактически " + index.lookupProbes() + " на " + LOOKUPS + " запросов. Перебор списка "
                        + "рецептов на каждый запрос резолвера съест тик сервера, иначе доска заказов лагает. При "
                        + "смене RecipeIndex.craftsFor проверь счётчик lookupProbes выше в тесте");
    }

    private static List<RecipeSpec> synthetic() {
        List<RecipeSpec> recipes = new ArrayList<>(RECIPES);
        for (int i = 0; i < RECIPES; i++) {
            recipes.add(craft("recipe_" + i, "item_" + i));
        }
        return recipes;
    }
}
