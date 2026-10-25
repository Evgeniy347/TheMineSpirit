package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class EnchantingPower {
    public static final String BOOKSHELF = "minecraft:bookshelf";
    public static final String ENCHANTING_TABLE = "minecraft:enchanting_table";
    public static final int SHELF_DISTANCE = 2;
    public static final int SHELF_HEIGHT = 1;
    public static final int MAX_SHELVES = 15;
    public static final int MAX_LEVEL = MAX_SHELVES * 2;
    private static final Set<String> AIR = Set.of("air", "cave_air", "void_air");

    private EnchantingPower() {}

    public static int shelfCount(Schematic schematic, BlockCoord table) {
        Objects.requireNonNull(schematic, "Схема не может быть null: по ней считают полки стола");
        Objects.requireNonNull(table, "Позиция стола не может быть null: вокруг неё ищут полки");
        int shelves = 0;
        for (int dy = 0; dy <= SHELF_HEIGHT; dy++) {
            for (int dx = -SHELF_DISTANCE; dx <= SHELF_DISTANCE; dx++) {
                for (int dz = -SHELF_DISTANCE; dz <= SHELF_DISTANCE; dz++) {
                    if (Math.abs(dx) != SHELF_DISTANCE && Math.abs(dz) != SHELF_DISTANCE) {
                        continue;
                    }
                    if (!Objects.equals(
                            path(BOOKSHELF),
                            path(blockAt(schematic, table.x() + dx, table.y() + dy, table.z() + dz)))) {
                        continue;
                    }
                    if (!isAir(schematic, table.x() + dx / 2, table.y() + dy, table.z() + dz / 2)) {
                        continue;
                    }
                    shelves++;
                }
            }
        }
        return shelves;
    }

    public static int level(Schematic schematic, BlockCoord table) {
        return Math.min(shelfCount(schematic, table), MAX_SHELVES) * 2;
    }

    public static List<BlockCoord> tables(Schematic schematic) {
        Objects.requireNonNull(schematic, "Схема не может быть null: в ней ищут столы чарования");
        List<BlockCoord> tables = new ArrayList<>();
        for (Schematic.Entry entry : schematic.blocks()) {
            if (Objects.equals(path(ENCHANTING_TABLE), path(entry.block()))) {
                tables.add(new BlockCoord(entry.dx(), entry.dy(), entry.dz()));
            }
        }
        return List.copyOf(tables);
    }

    private static boolean isAir(Schematic schematic, int x, int y, int z) {
        String block = path(blockAt(schematic, x, y, z));
        return block == null || AIR.contains(block);
    }

    private static @Nullable String blockAt(Schematic schematic, int x, int y, int z) {
        for (Schematic.Entry entry : schematic.blocks()) {
            if (entry.dx() == x && entry.dy() == y && entry.dz() == z) {
                return entry.block();
            }
        }
        return null;
    }

    private static @Nullable String path(@Nullable String blockId) {
        if (blockId == null) {
            return null;
        }
        int colon = blockId.indexOf(':');
        return colon < 0 ? blockId : blockId.substring(colon + 1);
    }
}
