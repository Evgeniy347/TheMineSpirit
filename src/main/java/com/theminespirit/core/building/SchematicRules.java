package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Правила качества схем: стены, вход, крыша, высота, окна, дверь, опора, сундук и свет. Чистая проверка
 * по блокам схемы без мира; применяется сторожем данных и тестами на фикстурах.
 */
public final class SchematicRules {
    public enum RuleId {
        CLOSED_WALLS("closed_walls"),
        ENTRANCE("entrance"),
        ROOF("roof"),
        INNER_HEIGHT("inner_height"),
        WINDOW("window"),
        DOOR("door"),
        SUPPORT("support"),
        CHEST_OPENABLE("chest_openable"),
        LIGHT("light");

        private final String id;

        RuleId(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Optional<RuleId> byId(String id) {
            Objects.requireNonNull(id, "Id правила не может быть null: по нему ищут правило схемы");
            for (RuleId rule : values()) {
                if (rule.id.equals(id)) {
                    return Optional.of(rule);
                }
            }
            return Optional.empty();
        }
    }

    public record Structure(String id, BuildingType type, Schematic schematic, BlockCoord entry) {
        public Structure {
            Objects.requireNonNull(id, "Id схемы не может быть null: по нему называют нарушителя");
            if (id.isBlank()) {
                throw new IllegalArgumentException("Id схемы пуст: по пустому имени нарушителя не найти");
            }
            Objects.requireNonNull(type, "Тип строения не может быть null: от него зависят правила жилья");
            Objects.requireNonNull(schematic, "Схема не может быть null: проверять нечего");
            Objects.requireNonNull(entry, "Точка входа не может быть null: без неё вход не проверить");
            if (entry.x() < 0
                    || entry.y() < 0
                    || entry.z() < 0
                    || entry.x() >= schematic.sizeX()
                    || entry.y() >= schematic.sizeY()
                    || entry.z() >= schematic.sizeZ()) {
                throw new IllegalArgumentException("Точка входа " + entry + " схемы '" + id + "' вне габаритов "
                        + schematic.sizeX() + "×" + schematic.sizeY() + "×" + schematic.sizeZ()
                        + ": по такой клетке вход не проверить");
            }
        }
    }

    public record Violation(RuleId rule, String message) {
        public Violation {
            Objects.requireNonNull(rule, "Правило нарушения не может быть null: без него неизвестно, что чинить");
            Objects.requireNonNull(message, "Сообщение нарушения не может быть null: без него не видно причину");
        }
    }

    private SchematicRules() {}

    public static List<Violation> check(Structure structure) {
        Objects.requireNonNull(structure, "Строение не может быть null: проверять нечего");
        Map<BlockCoord, String> grid = grid(structure.schematic());
        List<Violation> violations = new ArrayList<>();
        checkClosedWalls(structure, grid, violations);
        checkEntrance(structure, grid, violations);
        checkRoof(structure, grid, violations);
        checkInnerHeight(structure, grid, violations);
        if (structure.type() == BuildingType.HOUSING) {
            checkWindow(structure, grid, violations);
            checkDoor(structure, grid, violations);
            checkLight(grid, violations);
        }
        checkSupport(grid, violations);
        if (violations.stream().noneMatch(violation -> violation.rule() == RuleId.SUPPORT)) {
            checkMounting(structure.schematic(), grid, violations);
        }
        checkChests(grid, violations);
        return List.copyOf(violations);
    }

    private static Map<BlockCoord, String> grid(Schematic schematic) {
        Map<BlockCoord, String> grid = new LinkedHashMap<>();
        for (Schematic.Entry entry : schematic.blocks()) {
            grid.put(new BlockCoord(entry.dx(), entry.dy(), entry.dz()), entry.block());
        }
        return grid;
    }

    private static void checkClosedWalls(
            Structure structure, Map<BlockCoord, String> grid, List<Violation> violations) {
        Schematic schematic = structure.schematic();
        Set<BlockCoord> opening = entranceCells(structure);
        int eave = eave(schematic, grid);
        for (int y = 0; y <= eave; y++) {
            for (BlockCoord cell : perimeter(schematic, y)) {
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

    private static int eave(Schematic schematic, Map<BlockCoord, String> grid) {
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

    private static Set<BlockCoord> entranceCells(Structure structure) {
        BlockCoord entry = structure.entry();
        Set<BlockCoord> cells = new LinkedHashSet<>();
        cells.add(entry);
        if (entry.y() + 1 < structure.schematic().sizeY()) {
            cells.add(new BlockCoord(entry.x(), entry.y() + 1, entry.z()));
        }
        return cells;
    }

    private static Set<BlockCoord> perimeter(Schematic schematic, int y) {
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

    private static void checkEntrance(Structure structure, Map<BlockCoord, String> grid, List<Violation> violations) {
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

    private static int[] outwardNormal(BlockCoord entry, Schematic schematic) {
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

    private static void checkRoof(Structure structure, Map<BlockCoord, String> grid, List<Violation> violations) {
        Schematic schematic = structure.schematic();
        for (int x = 1; x < schematic.sizeX() - 1; x++) {
            for (int z = 1; z < schematic.sizeZ() - 1; z++) {
                int top = highestBlock(grid, x, schematic.sizeY(), z);
                if (top >= 2 && covers(grid.get(new BlockCoord(x, top, z)))) {
                    continue;
                }
                violations.add(new Violation(
                        RuleId.ROOF,
                        "крыша не закрывает внутреннюю клетку [" + x + ",*," + z
                                + "]: сверху над ней нет ни крыши, ни потолка — льёт дождь и светит солнце"));
                return;
            }
        }
    }

    private static int highestBlock(Map<BlockCoord, String> grid, int x, int sizeY, int z) {
        for (int y = sizeY - 1; y >= 0; y--) {
            if (grid.containsKey(new BlockCoord(x, y, z))) {
                return y;
            }
        }
        return -1;
    }

    private static void checkInnerHeight(
            Structure structure, Map<BlockCoord, String> grid, List<Violation> violations) {
        Schematic schematic = structure.schematic();
        for (int x = 1; x < schematic.sizeX() - 1; x++) {
            for (int z = 1; z < schematic.sizeZ() - 1; z++) {
                int floor = lowestBlock(grid, x, schematic.sizeY(), z);
                if (floor < 0) {
                    violations.add(new Violation(
                            RuleId.INNER_HEIGHT,
                            "у внутренней клетки [" + x + ",*," + z + "] нет пола: внутреннюю высоту не с чем мерить"));
                    return;
                }
                if (occupied(grid, x, floor, z)) {
                    continue;
                }
                int ceiling = ceiling(grid, x, floor, schematic.sizeY(), z);
                int height = (ceiling < 0 ? schematic.sizeY() : ceiling) - floor - 1;
                if (height < 2) {
                    violations.add(new Violation(
                            RuleId.INNER_HEIGHT,
                            "внутренняя высота " + height + " в клетке [" + x + ",*," + z
                                    + "]: в дом ниже 2 блоков не войти"));
                    return;
                }
            }
        }
    }

    private static boolean occupied(Map<BlockCoord, String> grid, int x, int floor, int z) {
        String above = grid.get(new BlockCoord(x, floor + 1, z));
        return above != null && (covers(above) || SchematicBlocks.isChest(above));
    }

    private static int ceiling(Map<BlockCoord, String> grid, int x, int floor, int sizeY, int z) {
        for (int y = floor + 2; y < sizeY; y++) {
            String block = grid.get(new BlockCoord(x, y, z));
            if (block != null && covers(block)) {
                return y;
            }
        }
        return -1;
    }

    private static boolean covers(String block) {
        if (SchematicBlocks.isChest(block)) {
            return false;
        }
        String path = SchematicBlocks.path(block);
        return SchematicBlocks.isFullBlock(block) || path.endsWith("_stairs") || path.endsWith("_slab");
    }

    private static int lowestBlock(Map<BlockCoord, String> grid, int x, int sizeY, int z) {
        for (int y = 0; y < sizeY; y++) {
            if (grid.containsKey(new BlockCoord(x, y, z))) {
                return y;
            }
        }
        return -1;
    }

    private static void checkWindow(Structure structure, Map<BlockCoord, String> grid, List<Violation> violations) {
        Schematic schematic = structure.schematic();
        for (Map.Entry<BlockCoord, String> entry : grid.entrySet()) {
            BlockCoord cell = entry.getKey();
            if (onPerimeter(cell, schematic) && SchematicBlocks.isWindow(entry.getValue())) {
                return;
            }
        }
        violations.add(new Violation(
                RuleId.WINDOW, "у жилья нет ни одного окна в стене: комнату не проветрить " + "и не осветить днём"));
    }

    private static void checkDoor(Structure structure, Map<BlockCoord, String> grid, List<Violation> violations) {
        BlockCoord entry = structure.entry();
        String lower = grid.get(entry);
        String upper = grid.get(new BlockCoord(entry.x(), entry.y() + 1, entry.z()));
        if (lower != null && upper != null && SchematicBlocks.isDoor(lower) && SchematicBlocks.isDoor(upper)) {
            return;
        }
        violations.add(new Violation(RuleId.DOOR, "в проёме входа нет двери: проём пуст или занят не дверью"));
    }

    private static void checkLight(Map<BlockCoord, String> grid, List<Violation> violations) {
        for (String block : grid.values()) {
            if (SchematicBlocks.isLightSource(block)) {
                return;
            }
        }
        violations.add(
                new Violation(RuleId.LIGHT, "в жилье нет источника света: внутри темно и ночью " + "появляются мобы"));
    }

    private static void checkSupport(Map<BlockCoord, String> grid, List<Violation> violations) {
        BlockSupport.World world = (x, y, z, face) -> {
            if (y < 0) {
                return true;
            }
            String block = grid.get(new BlockCoord(x, y, z));
            return block != null && SchematicBlocks.isFullBlock(block);
        };
        for (Map.Entry<BlockCoord, String> entry : grid.entrySet()) {
            BlockCoord cell = entry.getKey();
            if (cell.y() == 0 || upperHalfOfDoor(grid, cell, entry.getValue())) {
                continue;
            }
            if (!BlockSupport.hasSupport(world, cell.x(), cell.y(), cell.z())) {
                violations.add(new Violation(
                        RuleId.SUPPORT,
                        "у блока '" + entry.getValue() + "' в клетке [" + cell.x() + "," + cell.y() + "," + cell.z()
                                + "] нет опоры: блок повиснет или упадёт"));
                return;
            }
        }
    }

    private static void checkMounting(Schematic schematic, Map<BlockCoord, String> grid, List<Violation> violations) {
        for (Schematic.Entry entry : schematic.blocks()) {
            BlockCoord cell = new BlockCoord(entry.dx(), entry.dy(), entry.dz());
            BlockCoord mount = mountOf(entry, cell);
            if (mount == null || mount.y() < 0) {
                continue;
            }
            String holder = grid.get(mount);
            if (holder == null || !SchematicBlocks.isFullBlock(holder)) {
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

    private static BlockCoord mountOf(Schematic.Entry entry, BlockCoord cell) {
        if (SchematicBlocks.isWallMounted(entry.block())) {
            return switch (entry.state().getOrDefault("facing", "")) {
                case "north" -> new BlockCoord(cell.x(), cell.y(), cell.z() + 1);
                case "south" -> new BlockCoord(cell.x(), cell.y(), cell.z() - 1);
                case "east" -> new BlockCoord(cell.x() - 1, cell.y(), cell.z());
                case "west" -> new BlockCoord(cell.x() + 1, cell.y(), cell.z());
                default -> null;
            };
        }
        if (SchematicBlocks.path(entry.block()).endsWith("torch")) {
            return new BlockCoord(cell.x(), cell.y() - 1, cell.z());
        }
        return null;
    }

    private static boolean upperHalfOfDoor(Map<BlockCoord, String> grid, BlockCoord cell, String block) {
        return SchematicBlocks.isDoor(block)
                && block.equals(grid.get(new BlockCoord(cell.x(), cell.y() - 1, cell.z())));
    }

    private static void checkChests(Map<BlockCoord, String> grid, List<Violation> violations) {
        for (Map.Entry<BlockCoord, String> entry : grid.entrySet()) {
            if (!SchematicBlocks.isChest(entry.getValue())) {
                continue;
            }
            BlockCoord cell = entry.getKey();
            String above = grid.get(new BlockCoord(cell.x(), cell.y() + 1, cell.z()));
            // Как в ванили: крышку держит только блок, проводящий редстоун; сундук над сундуком (стопка) не мешает.
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

    private static boolean onPerimeter(BlockCoord cell, Schematic schematic) {
        return cell.x() == 0 || cell.x() == schematic.sizeX() - 1 || cell.z() == 0 || cell.z() == schematic.sizeZ() - 1;
    }
}
