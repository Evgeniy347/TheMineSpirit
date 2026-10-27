package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.BuildingType;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.SchematicDocument;
import com.theminespirit.core.building.SchematicTextParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PortalHallGuardTests {
    private static final Path FILE =
            Path.of("src/main/resources/" + PresetLibrary.SCHEMATICS_ROOT + "portal_hall_1.txt");
    private static final Set<String> STONE_SHELL = Set.of("stone", "stone_bricks", "stone_slab", "obsidian", "torch");
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private record Cell(int x, int y, int z) {}

    private static SchematicDocument document() throws IOException {
        return SchematicTextParser.parse(Files.readString(FILE), FILE.toString());
    }

    private static Map<Cell, String> grid(Schematic schematic) {
        Map<Cell, String> grid = new HashMap<>();
        for (Schematic.Entry entry : schematic.blocks()) {
            grid.put(new Cell(entry.dx(), entry.dy(), entry.dz()), entry.block());
        }
        return grid;
    }

    @Test
    void portalHall_Header_IsFortifiedStageThree() throws IOException {
        SchematicDocument document = document();

        assertEquals(
                Optional.of(BuildingType.FORTIFIED),
                document.header().type(),
                "Зал портала — отдельная укреплённая постройка, а не жильё, склад или ограда: тип задаёт свои "
                        + "сторожа и не должен попадать под правила окна, декора и забора");
        assertEquals(Optional.of(3), document.header().stage(), "Портал строится на этапе 3");
        assertEquals(1, document.header().level(), "Схема несут ступень 1: зажигание портала — отдельное событие");
        assertEquals(
                List.of(),
                document.exemptions(),
                "Схема обязана проходить всех сторожей без исключений: исключение ослабляет правило молча");
    }

    @Test
    void portalHall_Entrance_IsTwoDoorAirlock() throws IOException {
        Schematic schematic = document().schematic();
        Map<Cell, String> grid = grid(schematic);
        List<Schematic.Entry> doors = new ArrayList<>();
        for (Schematic.Entry entry : schematic.blocks()) {
            if (SchematicBlocks.isDoor(entry.block())
                    && !"upper".equals(entry.state().get("half"))) {
                doors.add(entry);
            }
        }
        doors.sort((first, second) -> Integer.compare(first.dz(), second.dz()));

        assertEquals(
                2,
                doors.size(),
                "Вход зала портала — ровно две двери подряд (шлюз), иначе из портала кто-то выйдет наружу мимо тамбура");
        Schematic.Entry outer = doors.get(0);
        Schematic.Entry inner = doors.get(1);
        assertEquals(outer.dy(), inner.dy(), "Двери шлюза стоят на одном уровне пола");
        int gap = Math.abs(outer.dx() - inner.dx()) + Math.abs(outer.dz() - inner.dz());
        assertTrue(
                gap >= 2 && gap <= 4,
                "Между дверями обязан быть тамбур, а не общая клетка и не длинный коридор: расстояние " + gap);

        Cell outside = new Cell(outer.dx(), outer.dy(), outer.dz() - 1);
        Cell hall = new Cell(7, inner.dy(), 6);
        Set<Cell> closed = new HashSet<>();
        closed.add(new Cell(outer.dx(), outer.dy(), outer.dz()));
        closed.add(new Cell(inner.dx(), inner.dy(), inner.dz()));
        assertTrue(
                reachable(grid, outside, hall, Set.of(), schematic.sizeX(), schematic.sizeZ()),
                "Сквозь открытый шлюз вход в зал обязан быть проходим");
        assertFalse(
                reachable(grid, outside, hall, closed, schematic.sizeX(), schematic.sizeZ()),
                "Закрытый шлюз обязан отрезать зал от улицы: иначе есть путь в обход двух дверей");
    }

    @Test
    void portalHall_Frame_IsFourByFiveAndUnlit() throws IOException {
        Schematic schematic = document().schematic();
        Map<Cell, String> grid = grid(schematic);
        List<Cell> obsidian = new ArrayList<>();
        for (Schematic.Entry entry : schematic.blocks()) {
            String path = SchematicBlocks.path(entry.block());
            assertFalse(
                    path.endsWith("nether_portal"),
                    "Рамка портала в схеме не зажжена: зажигание — отдельное событие доски");
            if ("obsidian".equals(path)) {
                obsidian.add(new Cell(entry.dx(), entry.dy(), entry.dz()));
            }
        }
        int minX = obsidian.stream().mapToInt(Cell::x).min().orElseThrow();
        int maxX = obsidian.stream().mapToInt(Cell::x).max().orElseThrow();
        int minY = obsidian.stream().mapToInt(Cell::y).min().orElseThrow();
        int maxY = obsidian.stream().mapToInt(Cell::y).max().orElseThrow();
        int minZ = obsidian.stream().mapToInt(Cell::z).min().orElseThrow();
        int maxZ = obsidian.stream().mapToInt(Cell::z).max().orElseThrow();

        assertEquals(4, maxX - minX + 1, "Рамка портала — 4 блока в ширину: 2×3 проход, как у ванильного порога");
        assertEquals(5, maxY - minY + 1, "Рамка портала — 5 блоков в высоту");
        assertEquals(minZ, maxZ, "Рамка стоит в одной плоскости: иначе это не рамка, а разбросанный обсидиан");
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                boolean ring = x == minX || x == maxX || y == minY || y == maxY;
                Cell cell = new Cell(x, y, minZ);
                if (ring) {
                    assertEquals(
                            "obsidian",
                            SchematicBlocks.path(grid.get(cell)),
                            "Рамка портала разомкнута в клетке [" + x + "," + y + "," + minZ + "]: проход не удержит");
                } else {
                    assertNull(grid.get(cell), "Внутри рамки обязан быть воздух: там станет портал");
                }
            }
        }
    }

    @Test
    void portalHall_Frame_HasTwoFreeBlocksAround() throws IOException {
        Schematic schematic = document().schematic();
        Map<Cell, String> grid = grid(schematic);
        List<Cell> obsidian = new ArrayList<>();
        for (Schematic.Entry entry : schematic.blocks()) {
            if ("obsidian".equals(SchematicBlocks.path(entry.block()))) {
                obsidian.add(new Cell(entry.dx(), entry.dy(), entry.dz()));
            }
        }
        int minX = obsidian.stream().mapToInt(Cell::x).min().orElseThrow() - 2;
        int maxX = obsidian.stream().mapToInt(Cell::x).max().orElseThrow() + 2;
        int minY = obsidian.stream().mapToInt(Cell::y).min().orElseThrow();
        int maxY = obsidian.stream().mapToInt(Cell::y).max().orElseThrow();
        int minZ = obsidian.stream().mapToInt(Cell::z).min().orElseThrow() - 2;
        int maxZ = obsidian.stream().mapToInt(Cell::z).max().orElseThrow() + 2;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY + 2; y++) {
                    Cell cell = new Cell(x, y, z);
                    boolean frame =
                            x >= minX + 2 && x <= maxX - 2 && y >= minY && y <= maxY && z >= minZ + 2 && z <= maxZ - 2;
                    if (frame) {
                        continue;
                    }
                    assertNull(
                            grid.get(cell),
                            "У рамки портала нет двух свободных блоков вокруг клетки [" + x + "," + y + "," + z
                                    + "]: духи держатся от портала на расстоянии ≥2");
                }
            }
        }
    }

    @Test
    void portalHall_Shell_IsStoneOnlyWithoutWindows() throws IOException {
        Schematic schematic = document().schematic();
        List<String> wooden = new ArrayList<>();
        for (Schematic.Entry entry : schematic.blocks()) {
            String path = SchematicBlocks.path(entry.block());
            if (path.endsWith("_door")) {
                continue;
            }
            if (SchematicBlocks.isWindow(entry.block()) || !STONE_SHELL.contains(path)) {
                wooden.add(path + "@" + entry.dx() + "," + entry.dy() + "," + entry.dz());
            }
        }

        assertEquals(
                List.of(),
                wooden,
                "Корпус зала портала — только камень, каменный кирпич, обсидиан и факелы: дерево горит, стекло — "
                        + "окно наружу, а постройка обязана быть глухой и огнестойкой. Лишнее");
    }

    private static boolean reachable(
            Map<Cell, String> grid, Cell from, Cell to, Set<Cell> barriers, int sizeX, int sizeZ) {
        Deque<Cell> queue = new ArrayDeque<>();
        Set<Cell> seen = new HashSet<>();
        queue.add(from);
        seen.add(from);
        while (!queue.isEmpty()) {
            Cell cell = queue.poll();
            if (cell.equals(to)) {
                return true;
            }
            for (int[] side : SIDES) {
                Cell next = new Cell(cell.x() + side[0], cell.y(), cell.z() + side[1]);
                if (next.x() < 0
                        || next.z() < 0
                        || next.x() >= sizeX
                        || next.z() >= sizeZ
                        || barriers.contains(next)
                        || seen.contains(next)
                        || !passable(grid, next)) {
                    continue;
                }
                seen.add(next);
                queue.add(next);
            }
        }
        return seen.contains(to);
    }

    private static boolean passable(Map<Cell, String> grid, Cell cell) {
        String block = grid.get(cell);
        if (block == null) {
            return true;
        }
        String path = SchematicBlocks.path(block);
        return path.endsWith("_door") || path.endsWith("torch");
    }
}
