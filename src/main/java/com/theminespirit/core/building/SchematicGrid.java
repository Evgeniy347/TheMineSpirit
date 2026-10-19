package com.theminespirit.core.building;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Сетка символов слоёв → блоки и клетки воздуха. Двухчастные блоки пишутся одним символом в обеих клетках:
 * дверь — в двух слоях друг над другом (нижняя половина ниже), кровать — в двух соседних клетках вдоль facing
 * (изголовье — по направлению facing). Половина или часть дописывается в состояние сама.
 */
final class SchematicGrid {
    record Result(List<Schematic.Entry> blocks, List<Schematic.Cell> air) {
    }

    private SchematicGrid() {
    }

    static Result resolve(char[][][] grid, Map<Character, SchematicTextParser.LegendEntry> legend, String origin) {
        int sy = grid.length;
        int sz = grid[0].length;
        int sx = grid[0][0].length;
        boolean[][][] done = new boolean[sy][sz][sx];
        List<Schematic.Entry> blocks = new ArrayList<>();
        List<Schematic.Cell> air = new ArrayList<>();
        Surface surface = new Surface(grid, done, blocks, origin);
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    char symbol = grid[y][z][x];
                    if (done[y][z][x] || symbol == SchematicTextParser.ANY) {
                        continue;
                    }
                    if (symbol == SchematicTextParser.AIR) {
                        air.add(new Schematic.Cell(x, y, z));
                        continue;
                    }
                    SchematicTextParser.LegendEntry entry = legend.get(symbol);
                    if (SchematicBlocks.isDoor(entry.block())) {
                        door(surface, entry, symbol, x, y, z);
                    } else if (SchematicBlocks.isBed(entry.block())) {
                        bed(surface, entry, symbol, x, y, z);
                    } else {
                        blocks.add(new Schematic.Entry(x, y, z, entry.block(), entry.state(), entry.text()));
                    }
                    done[y][z][x] = true;
                }
            }
        }
        return new Result(List.copyOf(blocks), List.copyOf(air));
    }

    private static void door(Surface surface, SchematicTextParser.LegendEntry entry, char symbol,
            int x, int y, int z) {
        char[][][] grid = surface.grid();
        if (y + 1 >= grid.length || grid[y + 1][z][x] != symbol || surface.done()[y + 1][z][x]) {
            throw new IllegalStateException("Файл постройки '" + surface.origin() + "': дверь '" + symbol
                    + "' в клетке [" + x + "," + y + "," + z + "] без верхней половины: напишите тот же символ в слое y"
                    + (y + 1) + " над ней (строка " + (z + 1) + ", позиция " + (x + 1) + ")");
        }
        surface.blocks().add(new Schematic.Entry(x, y, z, entry.block(), with(entry.state(), "half", "lower"),
                entry.text()));
        surface.blocks().add(new Schematic.Entry(x, y + 1, z, entry.block(), with(entry.state(), "half", "upper"),
                entry.text()));
        surface.done()[y + 1][z][x] = true;
    }

    private static void bed(Surface surface, SchematicTextParser.LegendEntry entry, char symbol,
            int x, int y, int z) {
        char[][][] grid = surface.grid();
        boolean[][][] done = surface.done();
        String facing = entry.state().get("facing");
        int dx = switch (facing) {
            case "east" -> 1;
            case "west" -> -1;
            default -> 0;
        };
        int dz = switch (facing) {
            case "south" -> 1;
            case "north" -> -1;
            default -> 0;
        };
        if (dx == 0 && dz == 0) {
            throw new IllegalStateException("Файл постройки '" + surface.origin() + "': у кровати '" + symbol
                    + "' facing=" + facing + ": допустимы north, south, east, west");
        }
        int footX = x;
        int footZ = z;
        if (!same(grid, done, symbol, y, x + dx, z + dz) && same(grid, done, symbol, y, x - dx, z - dz)) {
            footX = x - dx;
            footZ = z - dz;
        }
        int headX = footX + dx;
        int headZ = footZ + dz;
        if (!same(grid, done, symbol, y, headX, headZ) || !same(grid, done, symbol, y, footX, footZ)) {
            throw new IllegalStateException("Файл постройки '" + surface.origin() + "': кровать '" + symbol
                    + "' в клетке [" + x + "," + y + "," + z + "] без пары: вторая клетка тем же символом "
                    + "обязана стоять рядом вдоль facing=" + facing
                    + " — изголовье по направлению facing, ножная часть позади");
        }
        surface.blocks().add(new Schematic.Entry(footX, y, footZ, entry.block(), with(entry.state(), "part", "foot"),
                entry.text()));
        surface.blocks().add(new Schematic.Entry(headX, y, headZ, entry.block(), with(entry.state(), "part", "head"),
                entry.text()));
        done[y][headZ][headX] = true;
        done[y][footZ][footX] = true;
    }

    private static boolean same(char[][][] grid, boolean[][][] done, char symbol, int y, int x, int z) {
        return x >= 0 && x < grid[0][0].length && z >= 0 && z < grid[0].length
                && grid[y][z][x] == symbol && !done[y][z][x];
    }

    private static Map<String, String> with(Map<String, String> state, String key, String value) {
        Map<String, String> copy = new HashMap<>(state);
        copy.put(key, value);
        return Map.copyOf(copy);
    }

    private record Surface(char[][][] grid, boolean[][][] done, List<Schematic.Entry> blocks, String origin) {
    }
}
