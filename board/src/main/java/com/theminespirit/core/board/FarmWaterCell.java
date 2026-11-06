package com.theminespirit.core.board;

import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class FarmWaterCell {
    private static final String WATER = "water";

    private FarmWaterCell() {}

    public static Optional<BlockCoord> planned(Schematic scheme, BlockCoord origin) {
        List<BlockCoord> all = plannedAll(scheme, origin);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    public static List<BlockCoord> plannedAll(Schematic scheme, BlockCoord origin) {
        if (scheme == null || origin == null) {
            throw new IllegalArgumentException(
                    "Схема поля и её начало не могут быть null: " + "без них неизвестно, где по плану стоит вода");
        }
        List<BlockCoord> cells = new ArrayList<>();
        for (Schematic.PlacedEntry entry : scheme.entries(origin)) {
            if (isWater(entry.block())) {
                cells.add(entry.pos());
            }
        }
        return List.copyOf(cells);
    }

    public static boolean isWater(String block) {
        if (block == null || block.isBlank()) {
            return false;
        }
        String path = block.indexOf(':') < 0 ? block : block.substring(block.indexOf(':') + 1);
        return WATER.equals(path);
    }

    public static boolean field(String building) {
        if (building == null || building.isBlank()) {
            return false;
        }
        String id = building.toLowerCase(Locale.ROOT);
        return id.contains("field") || id.contains("farm");
    }
}
