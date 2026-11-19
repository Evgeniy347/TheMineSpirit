package com.theminespirit.core.building;

import static com.theminespirit.core.Lookup.present;

import com.theminespirit.core.building.SchematicRules.RuleId;
import com.theminespirit.core.building.SchematicRules.Structure;
import com.theminespirit.core.building.SchematicRules.Violation;
import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class SchematicShell {
    private static final String ANY_Y = ",*,";
    private static final int ROOFED_HEIGHT = 3;
    private static final int OPEN_HEIGHT = 2;

    private SchematicShell() {}

    static void checkClosedWalls(Structure structure, Map<BlockCoord, String> grid, List<Violation> violations) {
        Schematic schematic = structure.schematic();
        Set<BlockCoord> opening = entranceCells(structure, grid);
        int eave = Math.min(SchematicCells.eave(schematic, grid), SchematicCells.wallTop(schematic, grid));
        for (int y = 0; y <= eave; y++) {
            for (BlockCoord cell : SchematicCells.perimeter(schematic, y)) {
                if (grid.containsKey(cell) || opening.contains(cell)) {
                    continue;
                }
                violations.add(new Violation(
                        RuleId.CLOSED_WALLS,
                        "периметр слоя y=" + y + " разомкнут в клетке ["
                                + cell.x() + "," + cell.y() + "," + cell.z()
                                + "], а это не проём входа: стена с дырой не держит"));
                return;
            }
        }
    }

    static Set<BlockCoord> entranceCells(Structure structure, Map<BlockCoord, String> grid) {
        BlockCoord entry = structure.entry();
        Set<BlockCoord> cells = new LinkedHashSet<>();
        cells.add(entry);
        boolean upper = entry.y() + 1 < structure.schematic().sizeY();
        if (upper) {
            cells.add(new BlockCoord(entry.x(), entry.y() + 1, entry.z()));
        }
        Optional<SchematicBody.Normal> outward =
                SchematicBody.of(structure.schematic(), entry).outwardNormal(entry);
        if (outward.isEmpty()) {
            return cells;
        }
        int alongX = outward.get().dz();
        int alongZ = outward.get().dx();
        for (int sign : new int[] {1, -1}) {
            BlockCoord neighbor = new BlockCoord(entry.x() + alongX * sign, entry.y(), entry.z() + alongZ * sign);
            if (grid.containsKey(neighbor)) {
                continue;
            }
            cells.add(neighbor);
            if (upper) {
                cells.add(new BlockCoord(neighbor.x(), entry.y() + 1, neighbor.z()));
            }
        }
        return cells;
    }

    static void checkEntrance(Structure structure, Map<BlockCoord, String> grid, List<Violation> violations) {
        Schematic schematic = structure.schematic();
        BlockCoord entry = structure.entry();
        int[] normal = outwardNormal(entry, schematic);
        if (normal == null) {
            violations.add(new Violation(
                    RuleId.ENTRANCE,
                    "точка входа " + entry + " не на внешней грани: через внутреннюю клетку в постройку не войти"));
            return;
        }
        for (int dy = 0; dy <= 1; dy++) {
            int y = entry.y() + dy;
            if (y >= schematic.sizeY()) {
                continue;
            }
            String block = grid.get(new BlockCoord(entry.x(), y, entry.z()));
            if (block != null && !SchematicBlocks.isEntranceBlock(block)) {
                violations.add(new Violation(
                        RuleId.ENTRANCE,
                        "проём входа занят блоком '" + block + "' в клетке [" + entry.x() + "," + y + "," + entry.z()
                                + "]: вход обязан быть 1×2 и проходимым"));
                return;
            }
        }
        if (entry.y() > 1) {
            violations.add(new Violation(
                    RuleId.ENTRANCE,
                    "порог входа на высоте " + entry.y() + " над землёй: выше 1 блока в дом не подняться"));
        }
    }

    static int @Nullable [] outwardNormal(BlockCoord entry, Schematic schematic) {
        if (entry.z() == 0) {
            return new int[] {0, -1};
        }
        if (entry.z() == schematic.sizeZ() - 1) {
            return new int[] {0, 1};
        }
        if (entry.x() == 0) {
            return new int[] {-1, 0};
        }
        if (entry.x() == schematic.sizeX() - 1) {
            return new int[] {1, 0};
        }
        return null;
    }

    static void checkFireplace(Structure structure, Map<BlockCoord, String> grid, List<Violation> violations) {
        Schematic schematic = structure.schematic();
        for (Map.Entry<BlockCoord, String> cell : grid.entrySet()) {
            BlockCoord pos = cell.getKey();
            boolean perimeter = pos.x() == 0
                    || pos.z() == 0
                    || pos.x() == schematic.sizeX() - 1
                    || pos.z() == schematic.sizeZ() - 1;
            if (perimeter && SchematicBlocks.path(cell.getValue()).endsWith("campfire")) {
                violations.add(new Violation(
                        RuleId.FIREPLACE,
                        "костёр камина в клетке [" + pos.x() + "," + pos.y() + "," + pos.z()
                                + "] стоит в наружной стене: огонь виден снаружи дома — камин ставится внутрь на 1 блок, "
                                + "а стена за ним остаётся целой"));
                return;
            }
        }
    }

    static void checkRoof(Structure structure, Map<BlockCoord, String> grid, List<Violation> violations) {
        Schematic schematic = structure.schematic();
        for (int x = 1; x < schematic.sizeX() - 1; x++) {
            for (int z = 1; z < schematic.sizeZ() - 1; z++) {
                int top = SchematicCells.highestBlock(grid, x, schematic.sizeY(), z);
                if (top >= 2 && SchematicCells.covers(present(grid, new BlockCoord(x, top, z), "Сетка схемы"))) {
                    continue;
                }
                violations.add(new Violation(
                        RuleId.ROOF,
                        "крыша не закрывает внутреннюю клетку [" + x + ANY_Y + z
                                + "]: сверху над ней нет ни крыши, ни потолка — льёт дождь и светит солнце"));
                return;
            }
        }
    }

    static void checkInnerHeight(Structure structure, Map<BlockCoord, String> grid, List<Violation> violations) {
        Schematic schematic = structure.schematic();
        for (int x = 1; x < schematic.sizeX() - 1; x++) {
            for (int z = 1; z < schematic.sizeZ() - 1; z++) {
                int floor = SchematicCells.lowestBlock(grid, x, schematic.sizeY(), z);
                if (floor < 0) {
                    violations.add(new Violation(
                            RuleId.INNER_HEIGHT,
                            "у внутренней клетки [" + x + ANY_Y + z + "] нет пола: внутреннюю высоту не с чем мерить"));
                    return;
                }
                if (SchematicCells.occupied(grid, x, floor, z)) {
                    continue;
                }
                int ceiling = SchematicCells.ceiling(grid, x, floor, schematic.sizeY(), z);
                int height = (ceiling < 0 ? schematic.sizeY() : ceiling) - floor - 1;
                int required = ceiling < 0 ? OPEN_HEIGHT : ROOFED_HEIGHT;
                if (height < required) {
                    violations.add(
                            new Violation(
                                    RuleId.INNER_HEIGHT,
                                    "внутренняя высота " + height + " в клетке [" + x + ANY_Y + z + "] меньше "
                                            + required
                                            + ": под крышей ниже 3 блоков тесно (владелец 19.10), без крыши ниже 2 — не пройти"));
                    return;
                }
            }
        }
    }

    static void checkLevelCeiling(
            Structure structure, Map<BlockCoord, String> grid, int level, List<Violation> violations) {
        Schematic schematic = structure.schematic();
        int required = SchematicLevelRules.ceilingFor(level);
        for (int x = 1; x < schematic.sizeX() - 1; x++) {
            for (int z = 1; z < schematic.sizeZ() - 1; z++) {
                int floor = SchematicCells.lowestBlock(grid, x, schematic.sizeY(), z);
                if (floor < 0 || SchematicCells.occupied(grid, x, floor, z)) {
                    continue;
                }
                int ceiling = SchematicCells.ceiling(grid, x, floor, schematic.sizeY(), z);
                int height = (ceiling < 0 ? schematic.sizeY() : ceiling) - floor - 1;
                if (ceiling >= 0 && height < required) {
                    violations.add(new Violation(
                            RuleId.LEVEL_CEILING,
                            "высота внутри " + height + " в клетке [" + x + ANY_Y + z + "] меньше " + required
                                    + " для жилья уровня " + level
                                    + ": чем выше уровень, тем выше потолок (владелец 19.10)"));
                    return;
                }
            }
        }
    }
}
