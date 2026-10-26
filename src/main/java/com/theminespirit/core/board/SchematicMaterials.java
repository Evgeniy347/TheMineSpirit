package com.theminespirit.core.board;

import com.theminespirit.core.building.BuildingType;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class SchematicMaterials {
    private SchematicMaterials() {}

    public static Map<String, Integer> count(Optional<BuildingType> type, Supplier<Map<String, Integer>> blocks) {
        if (type.isEmpty() || type.get() == BuildingType.UNDERGROUND) {
            return Map.of();
        }
        return blocks.get();
    }
}
