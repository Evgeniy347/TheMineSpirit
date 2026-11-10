package com.theminespirit.core.board;

import com.theminespirit.core.building.BuildingType;
import com.theminespirit.core.building.Preset;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class SchematicMaterials {
    private SchematicMaterials() {}

    public static boolean known(Optional<BuildingType> type) {
        return type.isPresent();
    }

    public static Map<String, Integer> count(Optional<BuildingType> type, Supplier<Map<String, Integer>> blocks) {
        if (type.isEmpty() || type.get() == BuildingType.UNDERGROUND) {
            return Map.of();
        }
        return blocks.get();
    }

    public static Map<String, Integer> of(Optional<Preset> preset, Supplier<Map<String, Integer>> counted) {
        Objects.requireNonNull(preset, "Пресет схемы не может быть null: отсутствие задают Optional.empty()");
        Objects.requireNonNull(counted, "Счётчик материалов не может быть null: неизвестный файл надо посчитать");
        if (preset.isEmpty()) {
            return counted.get();
        }
        Preset found = preset.orElseThrow();
        return found.type() == BuildingType.UNDERGROUND ? Map.of() : found.materials();
    }
}
