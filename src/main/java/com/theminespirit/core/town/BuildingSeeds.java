package com.theminespirit.core.town;

import java.util.List;
import java.util.Objects;

public final class BuildingSeeds {
    private BuildingSeeds() {}

    public static boolean adjacentToStructure(List<String> neighbourIds) {
        Objects.requireNonNull(neighbourIds, "Соседи не могут быть null: по ним ищется постройка рядом");
        return neighbourIds.stream().anyMatch(BuildingSeeds::structurePart);
    }

    private static boolean structurePart(String blockId) {
        FloatingBlocks.Kind kind = FloatingBlocks.kind(blockId, false, false);
        return kind == FloatingBlocks.Kind.STATION
                || kind == FloatingBlocks.Kind.CHEST
                || kind == FloatingBlocks.Kind.REGISTERED_CHEST;
    }
}
