package com.theminespirit.core.board;

import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public record BuildingWoods(Map<String, String> chosen) {
    public static final BuildingWoods NONE = new BuildingWoods(Map.of());

    public BuildingWoods {
        Objects.requireNonNull(chosen, "Породы построек не могут быть null: пустые задают пустой картой");
        chosen.values().forEach(WoodSpecies::check);
        chosen = Map.copyOf(chosen);
    }

    public static String key(BoardCase building, BlockCoord origin) {
        Objects.requireNonNull(building, "Постройка не может быть null: порода выбирается на постройку");
        Objects.requireNonNull(origin, "Место постройки не может быть null: новая площадка — новая постройка");
        return building.name() + "@" + origin.x() + "," + origin.y() + "," + origin.z();
    }

    public Optional<String> of(String key) {
        return Optional.ofNullable(chosen.get(key));
    }

    public BuildingWoods with(String key, BuildingWood choice) {
        Objects.requireNonNull(key, "Ключ постройки не может быть null: порода пишется на постройку");
        Objects.requireNonNull(choice, "Выбор породы не может быть null: пишется выбранная порода");
        if (chosen.containsKey(key)) {
            return this;
        }
        Map<String, String> next = new TreeMap<>(chosen);
        next.put(key, choice.species());
        return new BuildingWoods(next);
    }

    public BuildingWoods rebuilt(String key) {
        if (!chosen.containsKey(key)) {
            return this;
        }
        Map<String, String> next = new TreeMap<>(chosen);
        next.remove(key);
        return new BuildingWoods(next);
    }

    public Collection<String> species() {
        return chosen.values();
    }
}
