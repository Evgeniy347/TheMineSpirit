package com.theminespirit.core.board;

import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class WarehouseChests {
    private WarehouseChests() {}

    public static List<BlockCoord> cells(BlockCoord origin, int level) {
        Objects.requireNonNull(origin, "Место склада не может быть null: клетки сундуков считаются от него");
        Schematic schematic = PresetLibrary.current()
                .byId(WarehouseRules.scheme(level))
                .orElseThrow(() -> new IllegalStateException("Схема склада '" + WarehouseRules.scheme(level)
                        + "' не найдена: клетки сундуков считать не из чего"))
                .schematic();
        List<BlockCoord> cells = new ArrayList<>();
        for (Schematic.Entry entry : schematic.blocks()) {
            if (chest(entry.block())) {
                cells.add(new BlockCoord(origin.x() + entry.dx(), origin.y() + entry.dy(), origin.z() + entry.dz()));
            }
        }
        return List.copyOf(cells);
    }

    private static boolean chest(String block) {
        String path = block.contains(":") ? block.substring(block.indexOf(':') + 1) : block;
        return "chest".equals(path) || "trapped_chest".equals(path);
    }
}
