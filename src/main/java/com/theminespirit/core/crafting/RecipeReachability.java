package com.theminespirit.core.crafting;

import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public final class RecipeReachability {
    private static final String MINECRAFT = "minecraft:";

    private final Obtainability obtainability;

    public RecipeReachability(RecipeIndex index, Predicate<String> gatherable) {
        Objects.requireNonNull(index, "Индекс рецептов не может быть null: без него нечего искать");
        Objects.requireNonNull(gatherable, "Проверка добычи не может быть null: без неё нет основания");
        this.obtainability = new Obtainability(
                item -> RecipeChooser.pick(index.craftsFor(normalize(item)), Map.of()),
                item -> index.smeltingFor(normalize(item)),
                item -> gatherable.test(normalize(item)));
    }

    public boolean canObtain(String item) {
        return obtainability.canObtain(normalize(item));
    }

    private static String normalize(String item) {
        Objects.requireNonNull(item, "Имя предмета не может быть null: по нему ищут путь");
        return item.startsWith(MINECRAFT) ? item.substring(MINECRAFT.length()) : item;
    }
}
