package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SchematicLight {
    private static final int[][] NEIGHBOURS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private SchematicLight() {}

    public static boolean hasSource(Schematic schematic) {
        for (Schematic.Entry entry : schematic.blocks()) {
            if (SchematicBlocks.lightLevel(entry.block(), entry.state()) > 0) {
                return true;
            }
        }
        return false;
    }

    public static List<BlockCoord> darkCells(Schematic schematic) {
        Map<BlockCoord, String> grid = new HashMap<>();
        for (Schematic.Entry entry : schematic.blocks()) {
            grid.put(new BlockCoord(entry.dx(), entry.dy(), entry.dz()), entry.block());
        }
        int[][][] light = light(schematic, grid);
        List<BlockCoord> dark = new ArrayList<>();
        for (int y = 0; y < schematic.sizeY(); y++) {
            for (int x = 1; x < schematic.sizeX() - 1; x++) {
                for (int z = 1; z < schematic.sizeZ() - 1; z++) {
                    BlockCoord cell = new BlockCoord(x, y, z);
                    if (light[x][y][z] == 0 && spawnable(grid, cell, schematic.sizeY())) {
                        dark.add(cell);
                    }
                }
            }
        }
        return List.copyOf(dark);
    }

    private static int[][][] light(Schematic schematic, Map<BlockCoord, String> grid) {
        int[][][] light = new int[schematic.sizeX()][schematic.sizeY()][schematic.sizeZ()];
        Deque<BlockCoord> queue = new ArrayDeque<>();
        for (Schematic.Entry entry : schematic.blocks()) {
            int level = SchematicBlocks.lightLevel(entry.block(), entry.state());
            if (level > 0) {
                light[entry.dx()][entry.dy()][entry.dz()] = level;
                queue.add(new BlockCoord(entry.dx(), entry.dy(), entry.dz()));
            }
        }
        while (!queue.isEmpty()) {
            BlockCoord cell = queue.poll();
            int next = light[cell.x()][cell.y()][cell.z()] - 1;
            if (next <= 0) {
                continue;
            }
            for (int[] step : NEIGHBOURS) {
                int x = cell.x() + step[0];
                int y = cell.y() + step[1];
                int z = cell.z() + step[2];
                if (x < 0
                        || y < 0
                        || z < 0
                        || x >= schematic.sizeX()
                        || y >= schematic.sizeY()
                        || z >= schematic.sizeZ()) {
                    continue;
                }
                BlockCoord neighbour = new BlockCoord(x, y, z);
                String block = grid.get(neighbour);
                if (light[x][y][z] >= next || (block != null && SchematicBlocks.blocksLight(block))) {
                    continue;
                }
                light[x][y][z] = next;
                queue.add(neighbour);
            }
        }
        return light;
    }

    private static boolean spawnable(Map<BlockCoord, String> grid, BlockCoord cell, int sizeY) {
        if (grid.containsKey(cell)) {
            return false;
        }
        String above = grid.get(new BlockCoord(cell.x(), cell.y() + 1, cell.z()));
        if (cell.y() + 1 < sizeY && above != null && SchematicBlocks.isFullBlock(above)) {
            return false;
        }
        if (cell.y() == 0) {
            return true;
        }
        String below = grid.get(new BlockCoord(cell.x(), cell.y() - 1, cell.z()));
        return below != null && SchematicBlocks.isSpawnSurface(below);
    }
}
