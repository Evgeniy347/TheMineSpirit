package com.theminespirit.core.perception;

import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.observation.ScanRadius;
import java.util.Objects;

public record LocateQuery(LocateType type, IdFilter filter, String species, ScanRadius radius) {
    public static final String ANY_SPECIES = "any";

    public LocateQuery {
        Objects.requireNonNull(type, "type не может быть null: запрос поиска обязан назвать, что искать");
        Objects.requireNonNull(filter, "filter не может быть null: «всё» задаётся IdFilter.matchAll()");
        Objects.requireNonNull(species, "species не может быть null: «любой вид» задаётся значением " + ANY_SPECIES);
        Objects.requireNonNull(radius, "radius не может быть null: радиус ограничивает стоимость поиска");
        if (type != LocateType.BLOCK && !filter.matchesAll()) {
            throw new IllegalArgumentException("Фильтр " + filter.patterns() + " задан для поиска " + type
                    + ": фильтр id применим только к поиску блока");
        }
        if (type != LocateType.TREE && !species.equals(ANY_SPECIES)) {
            throw new IllegalArgumentException("Вид '" + species + "' задан для поиска " + type
                    + ": вид применим только к поиску дерева");
        }
        requireSpeciesName(species);
    }

    public static LocateQuery block(IdFilter filter, ScanRadius radius) {
        return new LocateQuery(LocateType.BLOCK, filter, ANY_SPECIES, radius);
    }

    public static LocateQuery tree(String species, ScanRadius radius) {
        return new LocateQuery(LocateType.TREE, IdFilter.matchAll(), species, radius);
    }

    public static LocateQuery container(ScanRadius radius) {
        return new LocateQuery(LocateType.CONTAINER, IdFilter.matchAll(), ANY_SPECIES, radius);
    }

    private static void requireSpeciesName(String species) {
        if (species.isEmpty()) {
            throw new IllegalArgumentException("Вид дерева пуст: назовите вид (oak, birch) или " + ANY_SPECIES);
        }
        for (int i = 0; i < species.length(); i++) {
            char symbol = species.charAt(i);
            boolean allowed = (symbol >= 'a' && symbol <= 'z') || (symbol >= '0' && symbol <= '9') || symbol == '_';
            if (!allowed) {
                throw new IllegalArgumentException("Вид дерева '" + species + "' содержит недопустимый символ '"
                        + symbol + "': разрешены a-z, 0-9 и _ (например oak, dark_oak)");
            }
        }
    }
}
