package com.theminespirit.core.crafting;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record IngredientSpec(List<String> alternatives) {
    public IngredientSpec {
        Objects.requireNonNull(alternatives, "alternatives не может быть null: ингредиент без вариантов не выполнить");
        if (alternatives.isEmpty()) {
            throw new IllegalArgumentException(
                    "У ингредиента нет ни одного варианта предмета: такое место рецепта невыполнимо");
        }
        Set<String> seen = new HashSet<>();
        for (String alternative : alternatives) {
            if (alternative == null || alternative.isBlank()) {
                throw new IllegalArgumentException(
                        "Вариант ингредиента пуст: без имени предмета выбор из того, что есть у духа, невозможен");
            }
            if (!seen.add(alternative)) {
                throw new IllegalArgumentException("Вариант ингредиента '" + alternative
                        + "' повторён: дубликат искажает выбор и подсчёт нехватки");
            }
        }
        alternatives = List.copyOf(alternatives);
    }

    public static IngredientSpec of(String... alternatives) {
        return new IngredientSpec(List.of(alternatives));
    }
}
