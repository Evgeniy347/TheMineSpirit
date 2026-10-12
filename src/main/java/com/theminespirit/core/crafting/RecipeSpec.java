package com.theminespirit.core.crafting;

import java.util.List;
import java.util.Objects;

public record RecipeSpec(String id, String outputItem, int outputCount, RecipeKind kind, int width, int height,
        List<IngredientSpec> ingredients, int cookTicks) {
    public static final int GRID_SIDE = 3;
    public static final int INVENTORY_GRID_SIDE = 2;
    public static final int GRID_SLOTS = GRID_SIDE * GRID_SIDE;

    public RecipeSpec {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Id рецепта пуст: по нему выбор рецепта разрешает ничью");
        }
        if (outputItem == null || outputItem.isBlank()) {
            throw new IllegalArgumentException("Результат рецепта '" + id + "' пуст: рецепт без результата не нужен");
        }
        if (outputCount <= 0) {
            throw new IllegalArgumentException("Выход рецепта '" + id + "' должен быть больше 0, а получено "
                    + outputCount + ": расчёт числа крафтов делит на выход");
        }
        Objects.requireNonNull(kind, "kind не может быть null: от вида рецепта зависит, нужен ли верстак");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Размер рецепта '" + id + "' " + width + "×" + height
                    + " недопустим: ширина и высота обязаны быть положительными");
        }
        Objects.requireNonNull(ingredients, "ingredients не может быть null: рецепт без ингредиентов не выполнить");
        ingredients = List.copyOf(ingredients);
        checkShape(id, kind, width, height, ingredients, cookTicks);
    }

    public static RecipeSpec shaped(String id, String outputItem, int outputCount, int width, int height,
            List<IngredientSpec> ingredients) {
        return new RecipeSpec(id, outputItem, outputCount, RecipeKind.SHAPED, width, height, ingredients, 0);
    }

    public static RecipeSpec shapeless(String id, String outputItem, int outputCount,
            List<IngredientSpec> ingredients) {
        return new RecipeSpec(id, outputItem, outputCount, RecipeKind.SHAPELESS, 1, 1, ingredients, 0);
    }

    public static RecipeSpec smelting(String id, String outputItem, int outputCount, IngredientSpec input,
            int cookTicks) {
        return new RecipeSpec(id, outputItem, outputCount, RecipeKind.SMELTING, 1, 1, List.of(input), cookTicks);
    }

    public boolean needsTable() {
        return switch (kind) {
            case SHAPED -> width > INVENTORY_GRID_SIDE || height > INVENTORY_GRID_SIDE;
            case SHAPELESS -> ingredients.size() > INVENTORY_GRID_SIDE * INVENTORY_GRID_SIDE;
            case SMELTING -> false;
        };
    }

    private static void checkShape(String id, RecipeKind kind, int width, int height,
            List<IngredientSpec> ingredients, int cookTicks) {
        int slots = switch (kind) {
            case SHAPED -> {
                if (width > GRID_SIDE || height > GRID_SIDE) {
                    throw new IllegalArgumentException("Форма рецепта '" + id + "' " + width + "×" + height
                            + " не влезает в сетку " + GRID_SIDE + "×" + GRID_SIDE);
                }
                yield width * height;
            }
            case SHAPELESS -> GRID_SLOTS;
            case SMELTING -> 1;
        };
        if (ingredients.isEmpty() || ingredients.size() > slots) {
            throw new IllegalArgumentException("У рецепта '" + id + "' " + ingredients.size()
                    + " ингредиентов при допустимых от 1 до " + slots + ": форма и состав рецепта расходятся");
        }
        if (kind == RecipeKind.SMELTING && cookTicks <= 0) {
            throw new IllegalArgumentException("Время плавки рецепта '" + id + "' должно быть больше 0, а получено "
                    + cookTicks + ": по нему считается ожидание печи");
        }
        if (kind != RecipeKind.SMELTING && cookTicks != 0) {
            throw new IllegalArgumentException("У крафта '" + id + "' время плавки " + cookTicks
                    + ": оно бывает только у рецептов плавки");
        }
    }
}
