package com.theminespirit.core.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecipeChooserTests {
    private static final RecipeSpec STICK_FROM_PLANKS = RecipeSpec.shaped(
            "stick", "stick", 4, 1, 2, List.of(IngredientSpec.of("oak_planks"), IngredientSpec.of("oak_planks")));
    private static final RecipeSpec STICK_FROM_BAMBOO = RecipeSpec.shaped(
            "stick_from_bamboo", "stick", 1, 1, 2, List.of(IngredientSpec.of("bamboo"), IngredientSpec.of("bamboo")));
    private static final List<RecipeSpec> STICKS = List.of(STICK_FROM_BAMBOO, STICK_FROM_PLANKS);

    @Test
    void pick_OnlyBambooAvailable_ChoosesBambooRecipe() {
        assertEquals(
                Optional.of(STICK_FROM_BAMBOO),
                RecipeChooser.pick(STICKS, Map.of("bamboo", 2)),
                "С одним бамбуком в наличии выполним только рецепт из бамбука: выбор невыполнимого рецепта из досок "
                        + "заставил бы духа добывать лишнее при готовом материале");
    }

    @Test
    void pick_BothAvailable_ChoosesCheaperPerUnit() {
        assertEquals(
                Optional.of(STICK_FROM_PLANKS),
                RecipeChooser.pick(STICKS, Map.of("bamboo", 2, "oak_planks", 2)),
                "Когда выполнимы оба рецепта, нужен тот, что тратит меньше предметов на единицу результата: "
                        + "2 доски дают 4 палки, 2 бамбука — одну");
    }

    @Test
    void pick_NothingAvailable_ChoosesCheaperPerUnit() {
        assertEquals(
                Optional.of(STICK_FROM_PLANKS),
                RecipeChooser.pick(STICKS, Map.of()),
                "Если не выполним ни один рецепт, резолвер планирует добычу по самому дешёвому на единицу результата");
    }

    @Test
    void pick_FeasibleExpensiveAndInfeasibleCheap_ChoosesFeasible() {
        assertEquals(
                Optional.of(STICK_FROM_BAMBOO),
                RecipeChooser.pick(STICKS, Map.of("bamboo", 4)),
                "Выполнимость важнее цены: выполнимый рецепт из имеющегося бамбука обязан обойти дешёвый, "
                        + "для которого нет досок");
    }

    @Test
    void pick_EqualCost_ChoosesSmallerId() {
        RecipeSpec first = RecipeSpec.shapeless("a_recipe", "dye", 2, List.of(IngredientSpec.of("flower")));
        RecipeSpec second = RecipeSpec.shapeless("b_recipe", "dye", 2, List.of(IngredientSpec.of("petal")));

        assertEquals(
                Optional.of(first),
                RecipeChooser.pick(List.of(second, first), Map.of()),
                "При равной цене побеждает рецепт с меньшим id: иначе выбор зависит от порядка загрузки датапаков");
    }

    @Test
    void pick_NoCandidates_ReturnsEmpty() {
        assertEquals(
                Optional.empty(),
                RecipeChooser.pick(List.of(), Map.of("oak_planks", 9)),
                "Без кандидатов выбирать нечего: пустой результат, а не исключение");
    }
}
