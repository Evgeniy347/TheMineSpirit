package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Сторожа «чем выше уровень, тем богаче» (решения владельца 19.10): высота потолка
 * жилья, размер окна, доля декора — по уровню постройки; достижимость мебели и этажей пешком от входа и парадная
 * лестница шириной 2. Пороги — по уровням 1..4, выше 4 — как у 4; постройка без уровня (0) мерится как уровень 1.
 */
final class SchematicLevelRules {
    private static final int[] CEILING = {3, 4, 5, 6};
    private static final int[] WINDOW = {1, 2, 4, 6};
    private static final int[] DECOR_PERCENT = {3, 8, 12, 18};
    private static final int UPPER_FLOOR_RISE = 3;
    private static final int UPPER_FLOOR_MIN_CELLS = 6;
    private static final int GRAND_STAIRS_LEVEL = 3;
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
            if (seen.contains(cell) || !onPerimeter(cell, walls) || !SchematicBlocks.isWindow(entry.getValue())) {
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
            for (BlockCoord next : neighbours(cell)) {
                String block = grid.get(next);
                if (block != null && SchematicBlocks.isWindow(block) && onPerimeter(next, walls) && seen.add(next)) {
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
        if (DECOR.contains(path) || path.startsWith("potted_")) {
            return true;
        }
        for (String suffix : DECOR_SUFFIXES) {
            if (path.endsWith(suffix)) {
                return true;
            }
        }
        return path.endsWith("_stairs") && !onPerimeter(cell, schematic) && coveredAbove(cell, schematic, grid);
    }

    private static boolean coveredAbove(BlockCoord cell, Schematic schematic, Map<BlockCoord, String> grid) {
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

    static void checkStairs(
            SchematicRules.Structure walls,
            Map<BlockCoord, String> grid,
            SchematicWalk.Result walk,
            int level,
            List<SchematicRules.Violation> violations) {
        List<Set<BlockCoord>> floors = upperFloors(walls, grid);
        if (floors.isEmpty()) {
            return;
        }
        for (Set<BlockCoord> floor : floors) {
            if (floor.stream().noneMatch(walk.feet()::contains)) {
                BlockCoord any = floor.iterator().next();
                violations.add(
                        new SchematicRules.Violation(
                                SchematicRules.RuleId.STAIRS,
                                "этаж на высоте y=" + any.y() + " (" + floor.size()
                                        + " клеток) не достижим от входа по "
                                        + "ступенькам: нужна нормальная лестница, не приставная и без прыжков (владелец 19.10)"));
                return;
            }
        }
        if (level >= GRAND_STAIRS_LEVEL && !wideStairs(walk.risers())) {
            violations.add(new SchematicRules.Violation(
                    SchematicRules.RuleId.STAIRS,
                    "у постройки уровня " + level + " с этажами нет лестницы шириной 2: парадная лестница — "
                            + "две ступеньки рядом на каждом шаге (владелец 19.10)"));
        }
    }

    private static boolean wideStairs(Set<BlockCoord> risers) {
        for (BlockCoord riser : risers) {
            for (BlockCoord next : neighbours(riser)) {
                if (next.y() == riser.y() && risers.contains(next)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<Set<BlockCoord>> upperFloors(SchematicRules.Structure walls, Map<BlockCoord, String> grid) {
        Schematic schematic = walls.schematic();
        int minY = walls.entry().y() + UPPER_FLOOR_RISE;
        Set<BlockCoord> cells = new HashSet<>();
        for (int x = 1; x < schematic.sizeX() - 1; x++) {
            for (int z = 1; z < schematic.sizeZ() - 1; z++) {
                for (int y = minY; y < schematic.sizeY(); y++) {
                    BlockCoord cell = new BlockCoord(x, y, z);
                    if (SchematicWalk.standableIn(grid, schematic, cell) && coveredAbove(cell, schematic, grid)) {
                        cells.add(cell);
                    }
                }
            }
        }
        List<Set<BlockCoord>> floors = new ArrayList<>();
        Set<BlockCoord> seen = new HashSet<>();
        for (BlockCoord cell : cells) {
            if (!seen.add(cell)) {
                continue;
            }
            Set<BlockCoord> floor = new HashSet<>();
            Deque<BlockCoord> queue = new ArrayDeque<>(List.of(cell));
            while (!queue.isEmpty()) {
                BlockCoord current = queue.poll();
                floor.add(current);
                for (BlockCoord next : neighbours(current)) {
                    if (next.y() == current.y() && cells.contains(next) && seen.add(next)) {
                        queue.add(next);
                    }
                }
            }
            if (floor.size() >= UPPER_FLOOR_MIN_CELLS && hasSquare(floor)) {
                floors.add(floor);
            }
        }
        return floors;
    }

    private static boolean hasSquare(Set<BlockCoord> floor) {
        for (BlockCoord cell : floor) {
            if (floor.contains(new BlockCoord(cell.x() + 1, cell.y(), cell.z()))
                    && floor.contains(new BlockCoord(cell.x(), cell.y(), cell.z() + 1))
                    && floor.contains(new BlockCoord(cell.x() + 1, cell.y(), cell.z() + 1))) {
                return true;
            }
        }
        return false;
    }

    private static List<BlockCoord> neighbours(BlockCoord cell) {
        return List.of(
                new BlockCoord(cell.x() + 1, cell.y(), cell.z()),
                new BlockCoord(cell.x() - 1, cell.y(), cell.z()),
                new BlockCoord(cell.x(), cell.y(), cell.z() + 1),
                new BlockCoord(cell.x(), cell.y(), cell.z() - 1),
                new BlockCoord(cell.x(), cell.y() + 1, cell.z()),
                new BlockCoord(cell.x(), cell.y() - 1, cell.z()));
    }

    private static boolean onPerimeter(BlockCoord cell, Schematic schematic) {
        return cell.x() == 0 || cell.x() == schematic.sizeX() - 1 || cell.z() == 0 || cell.z() == schematic.sizeZ() - 1;
    }
}
