package com.theminespirit.core.town;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record TownStreets(ColumnMask main, ColumnMask side, List<TownStreets.Street> named) {

    public TownStreets {
        Objects.requireNonNull(main, "Главные улицы не могут быть null");
        Objects.requireNonNull(side, "Боковые улицы не могут быть null");
        named = List.copyOf(Objects.requireNonNull(named, "Список улиц не может быть null"));
    }

    public TownStreets(ColumnMask main, ColumnMask side) {
        this(main, side, List.of());
    }

    public boolean contains(int x, int z) {
        return main.contains(x, z) || side.contains(x, z);
    }

    public List<String> ids() {
        List<String> out = new ArrayList<>(named.size());
        for (Street street : named) {
            out.add(street.id());
        }
        return List.copyOf(out);
    }

    public Optional<String> idAt(int x, int z) {
        for (Street street : named) {
            if (street.cells().contains(x, z)) {
                return Optional.of(street.id());
            }
        }
        if (main.contains(x, z)) {
            return Optional.of(TownMapRules.MAIN);
        }
        return side.contains(x, z) ? Optional.of(TownMapRules.SIDE) : Optional.empty();
    }

    public Optional<Street> street(String id) {
        return named.stream().filter(street -> street.id().equals(id)).findFirst();
    }

    public record Street(String id, String kind, ColumnMask cells) {
        public Street {
            Objects.requireNonNull(id, "id улицы не может быть null: по нему собираются группы аудита");
            Objects.requireNonNull(kind, "Вид улицы не может быть null: по нему считается вес дороги");
            Objects.requireNonNull(cells, "Клетки улицы не могут быть null: без них группу не собрать");
        }

        public boolean isMain() {
            return TownMapRules.MAIN.equals(kind);
        }
    }
}
