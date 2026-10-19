package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Пеший обход схемы от клетки входа: где может стоять дух (клетка ног), если ходить по полу, подниматься только
 * по ступенькам и полублокам (без прыжков на полный блок и без приставных лестниц) и спускаться на 1. Снаружи
 * схемы — ровная земля. По обходу проверяются достижимость мебели и этажей и ширина парадной лестницы.
 */
final class SchematicWalk {
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final List<String> PASSABLE_SUFFIXES = List.of(
            "_door",
            "_carpet",
            "_pressure_plate",
            "_button",
            "_sign",
            "_banner",
            "_fence_gate",
            "_trapdoor",
            "_sapling",
            "_petals",
            "_rail",
            "_torch",
            "_tulip");
    private static final Set<String> PASSABLE = Set.of(
            "torch",
            "wall_torch",
            "ladder",
            "vine",
            "short_grass",
            "fern",
            "poppy",
            "dandelion",
            "cornflower",
            "allium",
            "azure_bluet",
            "oxeye_daisy",
            "blue_orchid",
            "lily_of_the_valley",
            "snow");
    private static final List<String> NOT_STANDABLE_SUFFIXES = List.of("_fence", "_wall", "_pane", "iron_bars");

    record Result(Set<BlockCoord> feet, Set<BlockCoord> risers) {}

    private final Map<BlockCoord, String> grid;
    private final Schematic schematic;

    private SchematicWalk(Map<BlockCoord, String> grid, Schematic schematic) {
        this.grid = grid;
        this.schematic = schematic;
    }

    static Result walk(Map<BlockCoord, String> grid, Schematic schematic, BlockCoord entry) {
        return new SchematicWalk(grid, schematic).run(entry);
    }

    static boolean passable(@Nullable String block) {
        if (block == null) {
            return true;
        }
        String path = SchematicBlocks.path(block);
        if (PASSABLE.contains(path)) {
            return true;
        }
        for (String suffix : PASSABLE_SUFFIXES) {
            if (path.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private Result run(BlockCoord entry) {
        Set<BlockCoord> feet = new LinkedHashSet<>();
        Set<BlockCoord> risers = new LinkedHashSet<>();
        Deque<BlockCoord> queue = new ArrayDeque<>();
        feet.add(entry);
        queue.add(entry);
        while (!queue.isEmpty()) {
            BlockCoord from = queue.poll();
            for (int[] side : SIDES) {
                BlockCoord next = step(from, side[0], side[1], risers);
                if (next != null && inside(next) && feet.add(next)) {
                    queue.add(next);
                }
            }
        }
        return new Result(Set.copyOf(feet), Set.copyOf(risers));
    }

    private @Nullable BlockCoord step(BlockCoord from, int dx, int dz, Set<BlockCoord> risers) {
        int x = from.x() + dx;
        int y = from.y();
        int z = from.z() + dz;
        if (open(x, y, z) && open(x, y + 1, z) && standable(x, y - 1, z)) {
            return new BlockCoord(x, y, z);
        }
        if (riser(x, y, z) && open(x, y + 1, z) && open(x, y + 2, z) && open(from.x(), y + 2, from.z())) {
            risers.add(new BlockCoord(x, y, z));
            return new BlockCoord(x, y + 1, z);
        }
        if (open(x, y, z) && open(x, y + 1, z) && open(x, y - 1, z) && standable(x, y - 2, z)) {
            return new BlockCoord(x, y - 1, z);
        }
        return null;
    }

    private boolean inside(BlockCoord cell) {
        return cell.x() >= -1
                && cell.x() <= schematic.sizeX()
                && cell.z() >= -1
                && cell.z() <= schematic.sizeZ()
                && cell.y() >= 0
                && cell.y() <= schematic.sizeY();
    }

    private boolean open(int x, int y, int z) {
        return passable(grid.get(new BlockCoord(x, y, z)));
    }

    boolean standable(int x, int y, int z) {
        if (y < 0) {
            return true;
        }
        String block = grid.get(new BlockCoord(x, y, z));
        if (block == null || passable(block)) {
            return false;
        }
        String path = SchematicBlocks.path(block);
        for (String suffix : NOT_STANDABLE_SUFFIXES) {
            if (path.endsWith(suffix)) {
                return false;
            }
        }
        return true;
    }

    private boolean riser(int x, int y, int z) {
        String block = grid.get(new BlockCoord(x, y, z));
        if (block == null) {
            return false;
        }
        String path = SchematicBlocks.path(block);
        return path.endsWith("_stairs") || path.endsWith("_slab");
    }

    static boolean standableIn(Map<BlockCoord, String> grid, Schematic schematic, BlockCoord feet) {
        SchematicWalk walk = new SchematicWalk(grid, schematic);
        return walk.open(feet.x(), feet.y(), feet.z())
                && walk.open(feet.x(), feet.y() + 1, feet.z())
                && walk.standable(feet.x(), feet.y() - 1, feet.z());
    }
}
