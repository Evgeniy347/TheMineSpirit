package com.theminespirit.core.building;

import java.util.Objects;
import java.util.Set;

public final class SchematicGround {
    private static final Set<String> SOIL = Set.of(
            "dirt",
            "grass_block",
            "podzol",
            "coarse_dirt",
            "rooted_dirt",
            "mycelium",
            "sand",
            "red_sand",
            "gravel",
            "clay");

    private SchematicGround() {}

    public static boolean groundLayer(Schematic schematic) {
        Objects.requireNonNull(schematic, "Схема не может быть null: без неё неизвестно, лежит ли низ на земле");
        boolean any = false;
        boolean all = true;
        for (Schematic.Entry entry : schematic.blocks()) {
            if (entry.dy() != 0 || entry.optional()) {
                continue;
            }
            any = true;
            if (SchematicActions.isActionPlaced(entry.block())) {
                return true;
            }
            if (!inGround(entry.block())) {
                all = false;
            }
        }
        return any && all;
    }

    public static boolean inGround(String blockId) {
        Objects.requireNonNull(blockId, "Блок не может быть null: без него неизвестно, лежит ли он в земле");
        String path = SchematicBlocks.path(blockId);
        return SchematicActions.isActionPlaced(blockId)
                || SchematicActions.isFluidPlaced(blockId)
                || SOIL.contains(path);
    }

    public static boolean tilled(Schematic schematic) {
        Objects.requireNonNull(schematic, "Схема не может быть null: без неё неизвестно, нужна ли тяпка");
        for (Schematic.Entry entry : schematic.blocks()) {
            if (entry.dy() == 0 && !entry.optional() && SchematicActions.isTilled(entry.block())) {
                return true;
            }
        }
        return false;
    }

    public static boolean walkwayPlaced(String wantedBlock, String cellBlock, String belowBlock, boolean cellPassable) {
        Objects.requireNonNull(wantedBlock, "Блок схемы не может быть null: без него нечего сверять");
        Objects.requireNonNull(cellBlock, "Блок клетки не может быть null: без него нечего сверять");
        Objects.requireNonNull(belowBlock, "Блок под клеткой не может быть null: тропа бывает в поверхности ниже");
        if (!SchematicActions.isWalkway(wantedBlock)) {
            return false;
        }
        return SchematicActions.isWalkway(cellBlock) || (cellPassable && SchematicActions.isWalkway(belowBlock));
    }
}
