package com.theminespirit.core.building;

import static com.theminespirit.core.Lookup.present;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Правила качества схем: стены, вход и ступенька к нему, крыша, высота, окна, дверь, опора, сундуки (открываются,
 * пары двойные) и свет. Чистая проверка
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
        CHEST_PAIR("chest_pair"),
        LIGHT("light"),
        ENTRANCE_STEP("entrance_step"),
        LEVEL_CEILING("level_ceiling"),
        WINDOW_SIZE("window_size"),
        DECOR("decor"),
        REACHABLE("reachable"),
        STAIRS("stairs"),
        DOUBLE_DOOR("double_door"),
        FIREPLACE("fireplace");

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

    public record Structure(String id, BuildingType type, Schematic schematic, BlockCoord entry, int level) {
        public Structure(String id, BuildingType type, Schematic schematic, BlockCoord entry) {
            this(id, type, schematic, entry, Preset.LEVEL_NONE);
        }

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

    private static final int ROOFED_HEIGHT = 3;
    private static final int OPEN_HEIGHT = 2;

    private SchematicRules() {}

    public static List<Violation> check(Structure structure) {
        Objects.requireNonNull(structure, "Строение не может быть null: проверять нечего");
        SchematicBody body = SchematicBody.of(structure.schematic(), structure.entry());
        Structure walls = body.covers(structure.schematic())
                ? structure
                : new Structure(
                        structure.id(),
                        structure.type(),
                        body.crop(structure.schematic()),
                        body.local(structure.entry()));
        Map<BlockCoord, String> grid = grid(walls.schematic());
        List<Violation> violations = new ArrayList<>();
        checkClosedWalls(walls, grid, violations);
        checkEntrance(walls, grid, violations);
        EntranceStep.problem(structure.schematic(), body, structure.entry())
                .ifPresent(problem -> violations.add(new Violation(RuleId.ENTRANCE_STEP, problem)));
        checkRoof(walls, grid, violations);
        checkInnerHeight(walls, grid, violations);
        int level = structure.level();
        if (structure.type() == BuildingType.HOUSING) {
            checkWindow(walls, grid, violations);
            checkDoor(walls, grid, violations);
            if (level > Preset.LEVEL_NONE) {
                EntrancePorch.problem(structure.schematic(), body, structure.entry())
                        .ifPresent(problem -> violations.add(new Violation(RuleId.ENTRANCE_STEP, problem)));
                checkLevelCeiling(walls, grid, level, violations);
                SchematicLevelRules.checkWindowSize(walls.schematic(), grid, level, violations);
            }
        }
        checkLight(walls.schematic(), violations);
        Map<BlockCoord, String> full = grid(structure.schematic());
        if (level > Preset.LEVEL_NONE
                && (structure.type() == BuildingType.HOUSING || structure.type() == BuildingType.SOCIAL)) {
            SchematicLevelRules.checkDecor(structure.schematic(), full, level, violations);
        }
        SchematicWalk.Result walk = SchematicWalk.walk(grid, walls.schematic(), walls.entry());
        SchematicLevelRules.checkReachable(grid, walk, violations);
        SchematicLevelRules.checkStairs(walls, grid, walk, level, violations);
        checkSupport(structure.schematic(), full, violations);
        if (violations.stream().noneMatch(violation -> violation.rule() == RuleId.SUPPORT)) {
            checkMounting(structure.schematic(), full, violations);
        }
        checkChests(full, violations);
        DoorPairs.problem(structure.schematic())
                .ifPresent(problem -> violations.add(new Violation(RuleId.DOUBLE_DOOR, problem)));
        checkFireplace(walls, grid, violations);
        ChestPairs.problem(structure.schematic())
                .ifPresent(problem -> violations.add(new Violation(RuleId.CHEST_PAIR, problem)));
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
        int eave = Math.min(eave(schematic, grid), wallTop(schematic, grid));
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

    private static int wallTop(Schematic schematic, Map<BlockCoord, String> grid) {
        for (int y = schematic.sizeY() - 1; y >= 0; y--) {
            for (BlockCoord cell : perimeter(schematic, y)) {
                String block = grid.get(cell);
                if (block != null && SchematicBlocks.lightLevel(block, Map.of()) == 0) {
                    return y;
                }
            }
        }
        return -1;
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

    private static int @Nullable [] outwardNormal(BlockCoord entry, Schematic schematic) {
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

    // Владелец 20.10: костёр камина в линии наружной стены виден снаружи — камин ставится внутрь на 1 блок.
    private static void checkFireplace(Structure structure, Map<BlockCoord, String> grid, List<Violation> violations) {
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

    private static void checkRoof(Structure structure, Map<BlockCoord, String> grid, List<Violation> violations) {
        Schematic schematic = structure.schematic();
        for (int x = 1; x < schematic.sizeX() - 1; x++) {
            for (int z = 1; z < schematic.sizeZ() - 1; z++) {
                int top = highestBlock(grid, x, schematic.sizeY(), z);
                if (top >= 2 && covers(present(grid, new BlockCoord(x, top, z), "Сетка схемы"))) {
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
                int required = ceiling < 0 ? OPEN_HEIGHT : ROOFED_HEIGHT;
                if (height < required) {
                    violations.add(
                            new Violation(
                                    RuleId.INNER_HEIGHT,
                                    "внутренняя высота " + height + " в клетке [" + x + ",*," + z + "] меньше "
                                            + required
                                            + ": под крышей ниже 3 блоков тесно (владелец 19.10), без крыши ниже 2 — не пройти"));
                    return;
                }
            }
        }
    }

    private static void checkLevelCeiling(
            Structure structure, Map<BlockCoord, String> grid, int level, List<Violation> violations) {
        Schematic schematic = structure.schematic();
        int required = SchematicLevelRules.ceilingFor(level);
        for (int x = 1; x < schematic.sizeX() - 1; x++) {
            for (int z = 1; z < schematic.sizeZ() - 1; z++) {
                int floor = lowestBlock(grid, x, schematic.sizeY(), z);
                if (floor < 0 || occupied(grid, x, floor, z)) {
                    continue;
                }
                int ceiling = ceiling(grid, x, floor, schematic.sizeY(), z);
                int height = (ceiling < 0 ? schematic.sizeY() : ceiling) - floor - 1;
                if (ceiling >= 0 && height < required) {
                    violations.add(new Violation(
                            RuleId.LEVEL_CEILING,
                            "высота внутри " + height + " в клетке [" + x + ",*," + z + "] меньше " + required
                                    + " для жилья уровня " + level
                                    + ": чем выше уровень, тем выше потолок (владелец 19.10)"));
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

    private static void checkLight(Schematic schematic, List<Violation> violations) {
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

    private static void checkSupport(Schematic schematic, Map<BlockCoord, String> grid, List<Violation> violations) {
        BlockSupport.World world = (x, y, z, face) -> {
            if (y < 0) {
                return true;
            }
            String block = grid.get(new BlockCoord(x, y, z));
            return block != null
                    && (SchematicBlocks.isFullBlock(block)
                            || (face == BlockSupport.Face.DOWN && SchematicBlocks.holdsHanging(block)));
        };
        for (Schematic.Entry block : schematic.blocks()) {
            BlockCoord cell = new BlockCoord(block.dx(), block.dy(), block.dz());
            if (cell.y() == 0 || upperHalfOfDoor(grid, cell, block.block())) {
                continue;
            }
            boolean supported = SchematicBlocks.isHanging(block.block(), block.state())
                    ? BlockSupport.hasHangingSupport(world, cell.x(), cell.y(), cell.z())
                    : (BlockSupport.hasSupport(world, cell.x(), cell.y(), cell.z())
                            || centerOnPost(block.block(), grid.get(new BlockCoord(cell.x(), cell.y() - 1, cell.z()))));
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

    private static @Nullable BlockCoord mountOf(Schematic.Entry entry, BlockCoord cell) {
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

    private static boolean centerOnPost(String block, @Nullable String below) {
        return below != null && SchematicBlocks.isCenterPost(below) && SchematicBlocks.standsOnCenter(block);
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
