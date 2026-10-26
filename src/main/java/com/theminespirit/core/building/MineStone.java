package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MineStone {
    private static final String AIR = "minecraft:air";
    private static final Set<String> COBBLE_DROPS = Set.of("minecraft:stone", "minecraft:cobblestone");

    private MineStone() {}

    public static int floorsToBring(MineLayout layout, Map<BlockCoord, String> blocks) {
        Objects.requireNonNull(layout, "Схема шахты не может быть null: по ней считают пол и проходку");
        Objects.requireNonNull(blocks, "Блоки мира не могут быть null: пол и камень проходки считают по ним");
        int floors = 0;
        for (BlockCoord cell : layout.floors()) {
            if (!SchematicBlocks.isFloorClass(blocks.getOrDefault(cell, AIR))) {
                floors++;
            }
        }
        int stone = 0;
        for (BlockCoord cell : layout.air()) {
            if (COBBLE_DROPS.contains(blocks.getOrDefault(cell, AIR))) {
                stone++;
            }
        }
        return Math.max(0, floors - stone);
    }
}
