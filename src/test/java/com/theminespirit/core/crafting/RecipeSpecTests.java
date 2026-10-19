package com.theminespirit.core.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecipeSpecTests {
    private static final IngredientSpec PLANKS = IngredientSpec.of("oak_planks");
    private static final IngredientSpec STICK = IngredientSpec.of("stick");

    @Test
    void ingredientSpec_EmptyAlternatives_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IngredientSpec(List.of()),
                "Ингредиент без вариантов обязан отвергаться: такое место рецепта невыполнимо, и ассигнатор "
                        + "молча объявил бы весь рецепт невыполнимым");
    }

    @Test
    void ingredientSpec_DuplicateAlternatives_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> IngredientSpec.of("oak_planks", "oak_planks"),
                "Повтор варианта обязан отвергаться: дубликат искажает подсчёт нехватки");
    }

    @Test
    void ingredientSpec_BlankAlternative_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> IngredientSpec.of("oak_planks", " "),
                "Пустое имя варианта обязано отвергаться: по нему нельзя найти предмет у духа");
    }

    @Test
    void recipeSpec_ZeroOutput_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RecipeSpec.shapeless("r", "stick", 0, List.of(PLANKS)),
                "Нулевой выход обязан отвергаться: число крафтов считается делением на выход");
    }

    @Test
    void recipeSpec_ZeroWidth_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RecipeSpec.shaped("r", "stick", 4, 0, 2, List.of(PLANKS, PLANKS)),
                "Нулевая ширина обязана отвергаться: форма из нуля клеток не существует");
    }

    @Test
    void recipeSpec_ShapeWiderThanGrid_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RecipeSpec.shaped("r", "stick", 4, 4, 1, List.of(PLANKS)),
                "Форма шире сетки 3×3 обязана отвергаться: в игре такой рецепт не бывает");
    }

    @Test
    void recipeSpec_MoreIngredientsThanCells_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RecipeSpec.shaped("r", "stick", 4, 1, 2, List.of(PLANKS, PLANKS, PLANKS)),
                "Ингредиентов больше клеток формы обязано отвергаться: форма и состав рецепта расходятся");
    }

    @Test
    void recipeSpec_SmeltingWithoutCookTime_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RecipeSpec.smelting("r", "iron_ingot", 1, IngredientSpec.of("raw_iron"), 0),
                "Плавка без времени обязана отвергаться: по нему считается ожидание печи");
    }

    @Test
    void needsTable_TwoByOneShape_False() {
        RecipeSpec sticks = RecipeSpec.shaped("stick", "stick", 4, 1, 2, List.of(PLANKS, PLANKS));

        assertFalse(
                sticks.needsTable(),
                "Палки 1×2 крафтятся в сетке 2×2 инвентаря: станция не нужна, "
                        + "иначе резолвер строит верстак ради палок");
    }

    @Test
    void needsTable_ThreeByThreePickaxe_True() {
        RecipeSpec pickaxe = RecipeSpec.shaped(
                "wooden_pickaxe", "wooden_pickaxe", 1, 3, 3, List.of(PLANKS, PLANKS, PLANKS, STICK, STICK));

        assertTrue(
                pickaxe.needsTable(),
                "Кирка 3×3 не влезает в сетку инвентаря: без верстака дух " + "попытался бы крафтить невозможное");
    }

    @Test
    void needsTable_ShapelessButton_False() {
        RecipeSpec button = RecipeSpec.shapeless("oak_button", "oak_button", 1, List.of(PLANKS));

        assertFalse(button.needsTable(), "Бесформенный рецепт из одного ингредиента идёт в инвентаре");
    }

    @Test
    void needsTable_ShapelessFiveIngredients_True() {
        RecipeSpec five = RecipeSpec.shapeless("five", "dye", 1, List.of(PLANKS, PLANKS, PLANKS, PLANKS, PLANKS));

        assertTrue(five.needsTable(), "Бесформенный рецепт из 5 ингредиентов не влезает в 4 клетки инвентаря");
    }

    @Test
    void needsTable_Smelting_False() {
        RecipeSpec smelt = RecipeSpec.smelting("iron", "iron_ingot", 1, IngredientSpec.of("raw_iron"), 200);

        assertFalse(smelt.needsTable(), "Плавка идёт в печи, а не в сетке крафта: верстак ей не нужен");
    }

    @Test
    void ingredients_ListCopy_DoesNotFollowSourceChanges() {
        List<IngredientSpec> source = new ArrayList<>(List.of(PLANKS));
        RecipeSpec recipe = RecipeSpec.shapeless("r", "stick", 1, source);
        source.add(STICK);

        assertEquals(
                1,
                recipe.ingredients().size(),
                "Рецепт обязан копировать список ингредиентов: иначе внешняя правка меняет рецепт в индексе");
    }
}
