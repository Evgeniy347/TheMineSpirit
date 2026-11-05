package com.theminespirit.neoforge;

import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicActions;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class SchematicWalkways {
    private SchematicWalkways() {}

    static Set<BlockCoord> free(List<Schematic.PlacedEntry> entries, Set<BlockCoord> cells) {
        return cells.stream().filter(cell -> !walkway(entries, cell)).collect(Collectors.toSet());
    }

    private static boolean walkway(List<Schematic.PlacedEntry> entries, BlockCoord cell) {
        return entries.stream()
                .anyMatch(entry -> entry.pos().equals(cell) && SchematicActions.isWalkway(entry.block()));
    }
}
