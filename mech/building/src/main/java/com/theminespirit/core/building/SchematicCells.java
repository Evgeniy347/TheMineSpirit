package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class SchematicCells {
    private SchematicCells() {}

    static Map<BlockCoord, String> of(Schematic schematic) {
        Map<BlockCoord, String> grid = new LinkedHashMap<>();
        for (Schematic.Entry entry : schematic.blocks()) {
            grid.put(new BlockCoord(entry.dx(), entry.dy(), entry.dz()), entry.block());
        }
        return grid;
    }

    static int eave(Schematic schematic, Map<BlockCoord, String> grid) {
        int eave = schematic.sizeY() - 1;
        for (int x = 1; x < schematic.sizeX() - 1; x++) {
            for (int z = 1; z < schematic.sizeZ() - 1; z++) {
                int floor = lowestBlock(grid, x, schematic.sizeY(), z);
                int ceiling = floor < 0 ? -1 : ceiling(grid, x, floor, schematic.sizeY(), z);
                if (ceiling > 0) {
                    eave = Math.min(eave, ceiling - 1);
                }
            }
        }
        return eave;
    }

    static int wallTop(Schematic schematic, Map<BlockCoord, String> grid) {
        for (int y = schematic.sizeY() - 1; y >= 0; y--) {
            for (BlockCoord cell : perimeter(schematic, y)) {
                String block = grid.get(cell);
                if (block != null && SchematicLightLevels.lightLevel(block, Map.of()) == 0) {
                    return y;
                }
            }
        }
        return -1;
    }

    static Set<BlockCoord> perimeter(Schematic schematic, int y) {
        Set<BlockCoord> cells = new LinkedHashSet<>();
        for (int x = 0; x < schematic.sizeX(); x++) {
            for (int z = 0; z < schematic.sizeZ(); z++) {
                if (x == 0 || x == schematic.sizeX() - 1 || z == 0 || z == schematic.sizeZ() - 1) {
                    cells.add(new BlockCoord(x, y, z));
                }
            }
        }
        return cells;
    }

    static int highestBlock(Map<BlockCoord, String> grid, int x, int sizeY, int z) {
        for (int y = sizeY - 1; y >= 0; y--) {
            if (grid.containsKey(new BlockCoord(x, y, z))) {
                return y;
            }
        }
        return -1;
    }

    static boolean occupied(Map<BlockCoord, String> grid, int x, int floor, int z) {
        String above = grid.get(new BlockCoord(x, floor + 1, z));
        return above != null && (covers(above) || SchematicBlocks.isChest(above));
    }

    static int ceiling(Map<BlockCoord, String> grid, int x, int floor, int sizeY, int z) {
        for (int y = floor + 2; y < sizeY; y++) {
            String block = grid.get(new BlockCoord(x, y, z));
            if (block != null && covers(block)) {
                return y;
            }
        }
        return -1;
    }

    static boolean covers(String block) {
        if (SchematicBlocks.isChest(block)) {
            return false;
        }
        String path = SchematicBlocks.path(block);
        return SchematicBlocks.isFullBlock(block) || path.endsWith("_stairs") || path.endsWith("_slab");
    }

    static int lowestBlock(Map<BlockCoord, String> grid, int x, int sizeY, int z) {
        for (int y = 0; y < sizeY; y++) {
            if (grid.containsKey(new BlockCoord(x, y, z))) {
                return y;
            }
        }
        return -1;
    }

    static boolean connects(Map<BlockCoord, String> grid, BlockCoord cell, int dx, int dz) {
        String neighbor = grid.get(new BlockCoord(cell.x() + dx, cell.y(), cell.z() + dz));
        return neighbor != null
                && (SchematicBlocks.isFullBlock(neighbor)
                        || SchematicBlocks.path(neighbor).endsWith("_pane"));
    }

    static boolean centerOnPost(String block, @Nullable String below) {
        return below != null && SchematicBlocks.isCenterPost(below) && SchematicBlocks.standsOnCenter(block);
    }

    static boolean upperHalfOfDoor(Map<BlockCoord, String> grid, BlockCoord cell, String block) {
        return SchematicBlocks.isDoor(block)
                && block.equals(grid.get(new BlockCoord(cell.x(), cell.y() - 1, cell.z())));
    }

    static boolean onPerimeter(BlockCoord cell, Schematic schematic) {
        return cell.x() == 0 || cell.x() == schematic.sizeX() - 1 || cell.z() == 0 || cell.z() == schematic.sizeZ() - 1;
    }
}
