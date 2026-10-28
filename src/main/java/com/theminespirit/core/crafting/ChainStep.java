package com.theminespirit.core.crafting;

import java.util.List;
import java.util.Objects;

public record ChainStep(RecipeSpec recipe, int crafts, List<String> assignment) {
    public ChainStep {
        Objects.requireNonNull(recipe, "Шаг цепочки без рецепта не выполнить");
        if (crafts <= 0) {
            throw new IllegalArgumentException(
                    "Шаг '" + recipe.id() + "' с " + crafts + " крафтами: нужен хотя бы один");
        }
        assignment = List.copyOf(Objects.requireNonNull(assignment, "Раскладка шага не может быть null"));
        if (assignment.size() != crafts * recipe.ingredients().size()) {
            throw new IllegalArgumentException("Раскладка шага '" + recipe.id() + "' несёт " + assignment.size()
                    + " предметов, а " + crafts + " крафтов по "
                    + recipe.ingredients().size()
                    + " мест требуют ровно их произведение");
        }
    }

    public List<String> itemsForCraft(int craft) {
        if (craft < 0 || craft >= crafts) {
            throw new IllegalArgumentException("Крафт " + craft + " вне диапазона [0, " + crafts + ")");
        }
        int size = recipe.ingredients().size();
        return assignment.subList(craft * size, (craft + 1) * size);
    }
}
