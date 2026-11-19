package com.theminespirit.core.building;

import com.theminespirit.core.building.SchematicRules.RuleId;
import com.theminespirit.core.building.SchematicRules.Structure;
import com.theminespirit.core.building.SchematicRules.Violation;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class SchematicFittings {
    private SchematicFittings() {}

    static void checkWindow(Structure structure, Map<BlockCoord, String> grid, List<Violation> violations) {
        Schematic schematic = structure.schematic();
        for (Map.Entry<BlockCoord, String> entry : grid.entrySet()) {
            BlockCoord cell = entry.getKey();
            if (SchematicCells.onPerimeter(cell, schematic) && SchematicBlocks.isWindow(entry.getValue())) {
                return;
            }
        }
        violations.add(new Violation(
                RuleId.WINDOW, "у жилья нет ни одного окна в стене: комнату не проветрить " + "и не осветить днём"));
    }

    static void checkDoor(Structure structure, Map<BlockCoord, String> grid, List<Violation> violations) {
        BlockCoord entry = structure.entry();
        String lower = grid.get(entry);
        String upper = grid.get(new BlockCoord(entry.x(), entry.y() + 1, entry.z()));
        if (lower != null && upper != null && SchematicBlocks.isDoor(lower) && SchematicBlocks.isDoor(upper)) {
            return;
        }
        violations.add(new Violation(RuleId.DOOR, "в проёме входа нет двери: проём пуст или занят не дверью"));
    }

    static void checkLight(Schematic schematic, List<Violation> violations) {
        if (!SchematicLight.hasSource(schematic)) {
            violations.add(new Violation(
                    RuleId.LIGHT,
                    "в постройке нет источника света: ночью внутри и у входа темно, появляются мобы — поставьте "
                            + "факел или фонарь (у открытой постройки — по периметру или у входа)"));
            return;
        }
        List<BlockCoord> dark = SchematicLight.darkCells(schematic);
        if (!dark.isEmpty()) {
            BlockCoord first = dark.get(0);
            violations.add(new Violation(
                    RuleId.LIGHT,
                    "внутри постройки " + dark.size() + " тёмных клеток (блочный свет 0), первая ["
                            + first.x() + "," + first.y() + "," + first.z()
                            + "]: там ночью появляются мобы — добавьте факел или фонарь ближе"));
        }
    }

    static void checkPanes(Map<BlockCoord, String> grid, List<Violation> violations) {
        for (Map.Entry<BlockCoord, String> entry : grid.entrySet()) {
            if (!SchematicBlocks.path(entry.getValue()).endsWith("_pane")) {
                continue;
            }
            BlockCoord cell = entry.getKey();
            boolean alongX = SchematicCells.connects(grid, cell, 1, 0) && SchematicCells.connects(grid, cell, -1, 0);
            boolean alongZ = SchematicCells.connects(grid, cell, 0, 1) && SchematicCells.connects(grid, cell, 0, -1);
            if (!alongX && !alongZ) {
                violations.add(new Violation(
                        RuleId.PANE,
                        "стеклянная панель [" + cell.x() + "," + cell.y() + "," + cell.z()
                                + "] не примыкает с двух сторон к полному блоку или панели: останется дыра — "
                                + "поставьте сюда полноценный блок стекла"));
            }
        }
    }

    static boolean supportedIn(Map<BlockCoord, String> grid, BlockCoord cell, String block, Map<String, String> state) {
        BlockSupport.World world = (x, y, z, face) -> {
            if (y < 0) {
                return true;
            }
            String near = grid.get(new BlockCoord(x, y, z));
            return near != null
                    && (SchematicBlocks.isFullBlock(near)
                            || (face == BlockSupport.Face.DOWN && SchematicBlocks.holdsHanging(near)));
        };
        return supportedIn(grid, world, cell, block, state);
    }

    static boolean supportedIn(
            Map<BlockCoord, String> grid,
            BlockSupport.World world,
            BlockCoord cell,
            String block,
            Map<String, String> state) {
        if (cell.y() == 0 || SchematicCells.upperHalfOfDoor(grid, cell, block)) {
            return true;
        }
        return SchematicBlocks.isHanging(block, state)
                ? BlockSupport.hasHangingSupport(world, cell.x(), cell.y(), cell.z())
                : (BlockSupport.hasSupport(world, cell.x(), cell.y(), cell.z())
                        || SchematicCells.centerOnPost(
                                block, grid.get(new BlockCoord(cell.x(), cell.y() - 1, cell.z()))));
    }

    static void checkSupport(Schematic schematic, Map<BlockCoord, String> grid, List<Violation> violations) {
        for (Schematic.Entry block : schematic.blocks()) {
            BlockCoord cell = new BlockCoord(block.dx(), block.dy(), block.dz());
            boolean supported = supportedIn(grid, cell, block.block(), block.state());
            if (!supported) {
                Map.Entry<BlockCoord, String> entry = Map.entry(cell, block.block());
                violations.add(new Violation(
                        RuleId.SUPPORT,
                        "у блока '" + entry.getValue() + "' в клетке [" + cell.x() + "," + cell.y() + "," + cell.z()
                                + "] нет опоры: блок повиснет или упадёт"));
                return;
            }
        }
    }

    static void checkMounting(Schematic schematic, Map<BlockCoord, String> grid, List<Violation> violations) {
        for (Schematic.Entry entry : schematic.blocks()) {
            BlockCoord cell = new BlockCoord(entry.dx(), entry.dy(), entry.dz());
            BlockCoord mount = mountOf(entry, cell);
            if (mount == null || mount.y() < 0) {
                continue;
            }
            String holder = grid.get(mount);
            if (holder == null
                    || (!SchematicBlocks.isFullBlock(holder) && !SchematicCells.centerOnPost(entry.block(), holder))) {
                violations.add(new Violation(
                        RuleId.SUPPORT,
                        "блок '" + entry.block() + "' в клетке [" + cell.x() + ","
                                + cell.y() + "," + cell.z() + "] крепится к клетке [" + mount.x() + "," + mount.y()
                                + ","
                                + mount.z() + "], а там " + (holder == null ? "пусто" : "'" + holder + "'")
                                + ": факел или табличка без полного блока за ней отвалится"));
                return;
            }
        }
    }

    static @Nullable BlockCoord mountOf(Schematic.Entry entry, BlockCoord cell) {
        return mountOf(entry.block(), entry.state(), cell);
    }

    static @Nullable BlockCoord mountOf(String block, Map<String, String> state, BlockCoord cell) {
        if (SchematicBlocks.isWallMounted(block)) {
            return switch (state.getOrDefault("facing", "")) {
                case "north" -> new BlockCoord(cell.x(), cell.y(), cell.z() + 1);
                case "south" -> new BlockCoord(cell.x(), cell.y(), cell.z() - 1);
                case "east" -> new BlockCoord(cell.x() - 1, cell.y(), cell.z());
                case "west" -> new BlockCoord(cell.x() + 1, cell.y(), cell.z());
                default -> null;
            };
        }
        if (SchematicBlocks.path(block).endsWith("torch")) {
            return new BlockCoord(cell.x(), cell.y() - 1, cell.z());
        }
        return null;
    }

    static void checkChests(Map<BlockCoord, String> grid, List<Violation> violations) {
        for (Map.Entry<BlockCoord, String> entry : grid.entrySet()) {
            if (!SchematicBlocks.isChest(entry.getValue())) {
                continue;
            }
            BlockCoord cell = entry.getKey();
            String above = grid.get(new BlockCoord(cell.x(), cell.y() + 1, cell.z()));
            if (above != null && SchematicBlocks.isFullBlock(above) && !SchematicBlocks.isChest(above)) {
                violations.add(new Violation(
                        RuleId.CHEST_OPENABLE,
                        "над сундуком в клетке ["
                                + cell.x() + "," + cell.y() + "," + cell.z() + "] стоит полный блок '" + above
                                + "': сундук не открыть"));
                return;
            }
        }
    }
}
