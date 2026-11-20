package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SchematicLevelRules {
    private static final int[] CEILING = {3, 4, 5, 6};
    private static final int[] WINDOW = {1, 2, 4, 6};
    private static final int[] DECOR_PERCENT = {3, 8, 12, 18};
    private static final int REACH_UP = 2;
    private static final int PERCENT = 100;
    private static final Set<String> STATIONS = Set.of(
            "crafting_table",
            "furnace",
            "smoker",
            "blast_furnace",
            "anvil",
            "grindstone",
            "smithing_table",
            "loom",
            "cartography_table",
            "fletching_table",
            "stonecutter",
            "brewing_stand",
            "enchanting_table",
            "lectern",
            "barrel",
            "composter",
            "bell");
    private static final List<String> DECOR_SUFFIXES = List.of(
            "_carpet",
            "_trapdoor",
            "_pressure_plate",
            "_banner",
            "_candle",
            "_sapling",
            "_petals",
            "_fence",
            "_leaves",
            "_tulip",
            "_azalea");
    private static final Set<String> DECOR = Set.of(
            "flower_pot",
            "lantern",
            "soul_lantern",
            "chain",
            "candle",
            "bell",
            "bookshelf",
            "chiseled_bookshelf",
            "lectern",
            "barrel",
            "campfire",
            "composter",
            "cauldron",
            "water_cauldron",
            "decorated_pot",
            "azalea",
            "poppy",
            "dandelion",
            "cornflower",
            "allium",
            "azure_bluet",
            "oxeye_daisy",
            "blue_orchid",
            "lily_of_the_valley");

    private SchematicLevelRules() {}

    static int ceilingFor(int level) {
        return byLevel(CEILING, level);
    }

    static int windowFor(int level) {
        return byLevel(WINDOW, level);
    }

    static int decorPercentFor(int level) {
        return byLevel(DECOR_PERCENT, level);
    }

    private static int byLevel(int[] table, int level) {
        int index = Math.max(1, Math.min(level, table.length)) - 1;
        return table[index];
    }

    static void checkWindowSize(
            Schematic walls, Map<BlockCoord, String> grid, int level, List<SchematicRules.Violation> violations) {
        int required = windowFor(level);
        int largest = 0;
        Set<BlockCoord> seen = new HashSet<>();
        for (Map.Entry<BlockCoord, String> entry : grid.entrySet()) {
            BlockCoord cell = entry.getKey();
            if (seen.contains(cell)
                    || !SchematicUpperFloors.onPerimeter(cell, walls)
                    || !SchematicBlocks.isWindow(entry.getValue())) {
                continue;
            }
            largest = Math.max(largest, windowPiece(cell, walls, grid, seen));
        }
        if (largest < required) {
            violations.add(new SchematicRules.Violation(
                    SchematicRules.RuleId.WINDOW_SIZE,
                    "самое большое окно — " + largest + " клеток стекла, а жилью уровня " + level + " нужно не меньше "
                            + required + ": чем выше уровень, тем больше окна (владелец 19.10)"));
        }
    }

    private static int windowPiece(
            BlockCoord start, Schematic walls, Map<BlockCoord, String> grid, Set<BlockCoord> seen) {
        Deque<BlockCoord> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        int size = 0;
        while (!queue.isEmpty()) {
            BlockCoord cell = queue.poll();
            size++;
            for (BlockCoord next : SchematicUpperFloors.neighbours(cell)) {
                String block = grid.get(next);
                if (block != null
                        && SchematicBlocks.isWindow(block)
                        && SchematicUpperFloors.onPerimeter(next, walls)
                        && seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        return size;
    }

    static void checkDecor(
            Schematic schematic, Map<BlockCoord, String> grid, int level, List<SchematicRules.Violation> violations) {
        int decor = 0;
        for (Map.Entry<BlockCoord, String> entry : grid.entrySet()) {
            if (isDecor(entry.getKey(), entry.getValue(), schematic, grid)) {
                decor++;
            }
        }
        int total = grid.size();
        int required = decorPercentFor(level);
        if (total > 0 && decor * PERCENT < required * total) {
            violations.add(new SchematicRules.Violation(
                    SchematicRules.RuleId.DECOR,
                    "декор — " + decor + " из " + total + " блоков (" + decor * PERCENT / total + " %), а уровню "
                            + level + " нужно не меньше " + required + " %: чем выше уровень, тем богаче убранство "
                            + "(ковры, цветы в горшках, фонари, полки, стулья — владелец 19.10)"));
        }
    }

    static boolean isDecor(BlockCoord cell, String block, Schematic schematic, Map<BlockCoord, String> grid) {
        String path = SchematicBlocks.path(block);
        if (isDecorBlock(block)) {
            return true;
        }
        return path.endsWith("_stairs")
                && !SchematicUpperFloors.onPerimeter(cell, schematic)
                && coveredAbove(cell, schematic, grid);
    }

    static boolean isDecorBlock(String block) {
        String path = SchematicBlocks.path(block);
        if (DECOR.contains(path) || path.startsWith("potted_")) {
            return true;
        }
        for (String suffix : DECOR_SUFFIXES) {
            if (path.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    static boolean isStation(String block) {
        return STATIONS.contains(SchematicBlocks.path(block));
    }

    static boolean coveredAbove(BlockCoord cell, Schematic schematic, Map<BlockCoord, String> grid) {
        for (int y = cell.y() + 1; y < schematic.sizeY(); y++) {
            if (grid.containsKey(new BlockCoord(cell.x(), y, cell.z()))) {
                return true;
            }
        }
        return false;
    }

    static void checkReachable(
            Map<BlockCoord, String> grid, SchematicWalk.Result walk, List<SchematicRules.Violation> violations) {
        for (Map.Entry<BlockCoord, String> entry : grid.entrySet()) {
            String path = SchematicBlocks.path(entry.getValue());
            boolean target = SchematicBlocks.isChest(path) || SchematicBlocks.isBed(path) || STATIONS.contains(path);
            if (!target || withinReach(entry.getKey(), walk.feet())) {
                continue;
            }
            BlockCoord cell = entry.getKey();
            violations.add(new SchematicRules.Violation(
                    SchematicRules.RuleId.REACHABLE,
                    "'" + entry.getValue() + "' в клетке [" + cell.x() + "," + cell.y() + "," + cell.z()
                            + "] не достать: от входа к нему не подойти пешком (мебель перегородила проход или нет "
                            + "лестницы из ступенек)"));
            return;
        }
    }

    private static boolean withinReach(BlockCoord target, Set<BlockCoord> feet) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -REACH_UP; dy <= 1; dy++) {
                    if (feet.contains(new BlockCoord(target.x() + dx, target.y() + dy, target.z() + dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
