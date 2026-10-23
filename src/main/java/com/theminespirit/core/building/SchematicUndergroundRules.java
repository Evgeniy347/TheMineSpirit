package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Сторожа подземной постройки: пол под каждой воздушной колонкой, высота прохода не меньше 3 и шаг между
 * соседними ступенями не больше 1. Свет в шахте необязателен: факелы — мягкие клетки, их может не быть.
 * Вход — верхняя клетка спуска для схемы с повтором вниз.
 */
public final class SchematicUndergroundRules {
    private static final int PASSAGE_HEIGHT = 3;

    private SchematicUndergroundRules() {}

    public static List<SchematicRules.Violation> check(SchematicRules.Structure structure, boolean descending) {
        Objects.requireNonNull(structure, "Подземная постройка не может быть null: проверять нечего");
        Schematic schematic = structure.schematic();
        Map<BlockCoord, String> blocks = new HashMap<>();
        Set<BlockCoord> air = new HashSet<>();
        for (Schematic.Entry entry : schematic.blocks()) {
            BlockCoord cell = new BlockCoord(entry.dx(), entry.dy(), entry.dz());
            // Необязательный факел в клетке прохода сквозной: дух проходит сквозь него, строитель копает клетку.
            if (entry.optional()) {
                air.add(cell);
            } else {
                blocks.put(cell, entry.block());
            }
        }
        for (Schematic.Cell cell : schematic.air()) {
            air.add(new BlockCoord(cell.dx(), cell.dy(), cell.dz()));
        }
        List<SchematicRules.Violation> violations = new ArrayList<>();
        Map<Long, Integer> floors = new HashMap<>();
        Map<Long, Integer> tops = new HashMap<>();
        for (int x = 0; x < schematic.sizeX(); x++) {
            for (int z = 0; z < schematic.sizeZ(); z++) {
                int lowest = Integer.MAX_VALUE;
                int top = Integer.MIN_VALUE;
                for (int y = 0; y < schematic.sizeY(); y++) {
                    if (air.contains(new BlockCoord(x, y, z))) {
                        lowest = Math.min(lowest, y);
                        top = Math.max(top, y);
                    }
                }
                if (lowest == Integer.MAX_VALUE) {
                    continue;
                }
                int floorY = lowest - 1;
                String floor = floorY < 0 ? null : blocks.get(new BlockCoord(x, floorY, z));
                if (floor == null || !SchematicBlocks.isFloorClass(floor)) {
                    violations.add(new SchematicRules.Violation(
                            SchematicRules.RuleId.FLOOR,
                            "под воздушной колонкой [" + x + ",*," + z + "] нет пола: там "
                                    + (floor == null ? "пусто" : "'" + floor + "'")
                                    + ", а пол обязан быть дешёвым твёрдым блоком (@floor)"));
                    continue;
                }
                int height = 0;
                while (air.contains(new BlockCoord(x, lowest + height, z))) {
                    height++;
                }
                if (height < PASSAGE_HEIGHT) {
                    violations.add(new SchematicRules.Violation(
                            SchematicRules.RuleId.PASSAGE_HEIGHT,
                            "высота прохода " + height + " в клетке [" + x + "," + lowest + "," + z + "] меньше "
                                    + PASSAGE_HEIGHT + ": дух не пройдёт и не выберется наверх"));
                    continue;
                }
                floors.put(column(x, z), floorY);
                tops.put(column(x, z), top);
            }
        }
        checkSteps(floors, violations);
        checkEntrance(structure, blocks, air, floors, tops, descending, violations);
        return List.copyOf(violations);
    }

    private static void checkSteps(Map<Long, Integer> floors, List<SchematicRules.Violation> violations) {
        int[][] sides = {{1, 0}, {0, 1}};
        for (Map.Entry<Long, Integer> cell : floors.entrySet()) {
            int x = (int) (cell.getKey() >> 32);
            int z = (int) (long) cell.getKey();
            for (int[] side : sides) {
                Integer near = floors.get(column(x + side[0], z + side[1]));
                if (near != null && Math.abs(near - cell.getValue()) > 1) {
                    violations.add(new SchematicRules.Violation(
                            SchematicRules.RuleId.PASSAGE_STEP,
                            "ступень между клетками [" + x + ",*," + z + "] и [" + (x + side[0]) + ",*," + (z + side[1])
                                    + "] выше 1 блока: дух не поднимется и не спустится пешком"));
                    return;
                }
            }
        }
    }

    private static void checkEntrance(
            SchematicRules.Structure structure,
            Map<BlockCoord, String> blocks,
            Set<BlockCoord> air,
            Map<Long, Integer> floors,
            Map<Long, Integer> tops,
            boolean descending,
            List<SchematicRules.Violation> violations) {
        BlockCoord entry = structure.entry();
        if (!air.contains(entry) || blocks.containsKey(entry)) {
            violations.add(new SchematicRules.Violation(
                    SchematicRules.RuleId.ENTRANCE,
                    "вход [" + entry.x() + "," + entry.y() + "," + entry.z() + "] не воздух: в шахту не войти"));
            return;
        }
        Integer floorY = floors.get(column(entry.x(), entry.z()));
        if (floorY == null) {
            violations.add(new SchematicRules.Violation(
                    SchematicRules.RuleId.ENTRANCE,
                    "вход [" + entry.x() + "," + entry.y() + "," + entry.z()
                            + "] не над полом прохода: в шахту не встать"));
            return;
        }
        if (descending) {
            int highest =
                    floors.values().stream().mapToInt(Integer::intValue).max().orElse(floorY);
            int top = tops.getOrDefault(column(entry.x(), entry.z()), entry.y());
            if (floorY != highest || entry.y() != top) {
                violations.add(new SchematicRules.Violation(
                        SchematicRules.RuleId.ENTRANCE,
                        "вход [" + entry.x() + "," + entry.y() + "," + entry.z()
                                + "] не верхняя клетка спуска: вниз ведёт самая высокая ступень, иначе дух "
                                + "начнёт спуск из середины"));
            }
        }
    }

    private static long column(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }
}
