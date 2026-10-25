package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicTextParser;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Сторож раскладки участка этапа 1 (решение 54b п.2): ничто не пересекается, всё внутри ограды. */
class SettlementLayoutTests {
    private static final Path ROOT = Path.of("src/main/resources/" + PresetLibrary.SCHEMATICS_ROOT);
    private static final BlockCoord ANCHOR = new BlockCoord(100, 64, -40);
    // Решение 54b п.2: вход в шахту — вне габарита дома с отступом не меньше 3.
    private static final int MINE_GAP = 3;
    // Спуск mine_descent_v1 выходит к поверхности первыми тремя ступенями (проход высотой 3).
    private static final int MINE_SURFACE_STEPS = 3;
    // Станции встают вокруг духа на дворе не дальше этого (поиск клетки StationPlacer).
    private static final int YARD_RADIUS = 2;

    @Test
    void schemes_FenceAndHut_MatchLayoutSizes() throws IOException {
        Schematic fence = load("fence");
        Schematic hut = load("hut_1");

        assertEquals(
                List.of(SettlementLayout.PLOT, SettlementLayout.PLOT),
                List.of(fence.sizeX(), fence.sizeZ()),
                "Ограда — периметр участка: её схема обязана совпадать с размером участка раскладки");
        assertEquals(
                List.of(SettlementLayout.HOUSE_WIDTH, SettlementLayout.HOUSE_DEPTH),
                List.of(hut.sizeX(), hut.sizeZ()),
                "Хижина ставится в центр участка по своему габариту: схема и раскладка обязаны совпадать");
    }

    @Test
    void gatherForbidden_PlotGroundAndCaveUnderSettlement_Forbidden() {
        BlockCoord pocket = new BlockCoord(ANCHOR.x() + 5, ANCHOR.y() - 7, ANCHOR.z() - 2);
        BlockCoord outsideDirt =
                new BlockCoord(ANCHOR.x() + SettlementLayout.LEVEL_HALF + 4, ANCHOR.y() - 1, ANCHOR.z());

        assertTrue(
                SettlementLayout.gatherForbidden(ANCHOR, pocket, 7),
                "Карман под участком — не место добычи земли: 4-й раз тот же карман (INC-69)");
        assertTrue(
                SettlementLayout.gatherForbidden(
                        ANCHOR, new BlockCoord(ANCHOR.x() + SettlementLayout.LEVEL_HALF + 6, 60, ANCHOR.z()), 6),
                "Пещера глубже 3 под поверхностью рядом с поселением — не цель добычи");
        assertFalse(
                SettlementLayout.gatherForbidden(ANCHOR, outsideDirt, 1),
                "Земля под дёрном за оградой — законная добыча на подсыпку");
        BlockCoord mine = SettlementLayout.mineEntrance(ANCHOR);
        assertFalse(
                SettlementLayout.gatherForbidden(ANCHOR, new BlockCoord(mine.x(), mine.y() - 5, mine.z() + 3), 6),
                "Шахта плана — исключение: камень добывается в ней");
    }

    @Test
    void planCell_FenceHutYardTorchMine_KeptFieldNot() {
        int y = ANCHOR.y();
        BlockCoord hut = SettlementLayout.houseOrigin(ANCHOR);
        BlockCoord yard = SettlementLayout.workYard(ANCHOR);
        BlockCoord torch = SettlementLayout.lighting(ANCHOR).get(0);
        BlockCoord mine = SettlementLayout.mineEntrance(ANCHOR);

        assertTrue(
                SettlementLayout.planCell(ANCHOR, ANCHOR.x() + SettlementLayout.HALF, y, ANCHOR.z()),
                "Линия ограды — клетка плана: переоткрытая расчистка её не сносит");
        assertTrue(SettlementLayout.planCell(ANCHOR, hut.x(), y + 2, hut.z()), "Хижина — клетка плана");
        assertTrue(SettlementLayout.planCell(ANCHOR, yard.x() + 1, y, yard.z()), "Двор станций — клетка плана");
        assertTrue(SettlementLayout.planCell(ANCHOR, torch.x(), y, torch.z()), "Факел света — клетка плана");
        assertTrue(SettlementLayout.planCell(ANCHOR, mine.x(), y - 6, mine.z() + 4), "Спуск в шахту — клетка плана");
        assertFalse(
                SettlementLayout.planCell(ANCHOR, ANCHOR.x() + 9, y + 1, ANCHOR.z() + 9),
                "Пустое поле участка — не клетка плана: его расчистка и выравнивание обрабатывают");
    }

    @Test
    void layout_EveryPart_InsideFenceAndDisjoint() {
        Map<String, List<Cell>> parts = new HashMap<>();
        parts.put(
                "дом",
                box(SettlementLayout.houseOrigin(ANCHOR), SettlementLayout.HOUSE_WIDTH, SettlementLayout.HOUSE_DEPTH));
        parts.put("шахта", mine());
        parts.put("двор", yard());
        parts.put(
                "факелы",
                SettlementLayout.lighting(ANCHOR).stream().map(Cell::of).toList());
        parts.put("путь от калитки", path());
        List<String> problems = new ArrayList<>();
        int half = SettlementLayout.HALF;
        parts.forEach((name, cells) -> cells.stream()
                .filter(cell -> Math.abs(cell.x() - ANCHOR.x()) >= half || Math.abs(cell.z() - ANCHOR.z()) >= half)
                .forEach(cell -> problems.add(name + " вне ограды: " + cell)));
        List<String> names = new ArrayList<>(parts.keySet());
        for (int i = 0; i < names.size(); i++) {
            for (int j = i + 1; j < names.size(); j++) {
                for (Cell cell : parts.get(names.get(i))) {
                    if (parts.get(names.get(j)).contains(cell)) {
                        problems.add(names.get(i) + " и " + names.get(j) + " в одной клетке " + cell);
                    }
                }
            }
        }

        assertEquals(List.of(), problems, "Раскладка участка: части не пересекаются и стоят внутри ограды");
    }

    @Test
    void mine_FromHouse_KeepsGap() {
        BlockCoord house = SettlementLayout.houseOrigin(ANCHOR);
        int houseEast = house.x() + SettlementLayout.HOUSE_WIDTH - 1;
        int gap = SettlementLayout.mineEntrance(ANCHOR).x() - houseEast - 1;

        assertTrue(
                gap >= MINE_GAP, "Вход в шахту — вне габарита дома с отступом не меньше " + MINE_GAP + ", а не " + gap);
    }

    @Test
    void fence_Gate_FacesHouseDoor() {
        BlockCoord gate = SettlementLayout.gate(ANCHOR);
        BlockCoord house = SettlementLayout.houseOrigin(ANCHOR);

        assertEquals(
                gate.x(),
                house.x() + SettlementLayout.HOUSE_WIDTH / 2,
                "Дверь хижины (середина фронта) смотрит на калитку: путь от калитки к двери прямой");
        assertEquals(
                new BlockCoord(gate.x() - SettlementLayout.HALF, gate.y(), gate.z()),
                SettlementLayout.fenceOrigin(ANCHOR),
                "Калитка — середина северной стороны ограды");
    }

    private static List<Cell> mine() {
        BlockCoord entrance = SettlementLayout.mineEntrance(ANCHOR);
        List<Cell> cells = new ArrayList<>();
        for (int step = 0; step < MINE_SURFACE_STEPS; step++) {
            for (int side = -1; side <= 1; side++) {
                cells.add(new Cell(entrance.x() + side, entrance.z() + step));
            }
        }
        return cells;
    }

    private static List<Cell> yard() {
        BlockCoord yard = SettlementLayout.workYard(ANCHOR);
        int r = YARD_RADIUS;
        return box(new BlockCoord(yard.x() - r, yard.y(), yard.z() - r), 2 * r + 1, 2 * r + 1);
    }

    private static List<Cell> path() {
        BlockCoord gate = SettlementLayout.gate(ANCHOR);
        BlockCoord house = SettlementLayout.houseOrigin(ANCHOR);
        List<Cell> cells = new ArrayList<>();
        for (int z = gate.z() + 1; z < house.z(); z++) {
            cells.add(new Cell(gate.x(), z));
        }
        return cells;
    }

    private static List<Cell> box(BlockCoord origin, int width, int depth) {
        List<Cell> cells = new ArrayList<>();
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < depth; dz++) {
                cells.add(new Cell(origin.x() + dx, origin.z() + dz));
            }
        }
        return cells;
    }

    private static Schematic load(String id) throws IOException {
        Path file = ROOT.resolve(id + ".txt");
        return SchematicTextParser.parse(Files.readString(file), file.toString())
                .schematic();
    }

    private record Cell(int x, int z) {
        static Cell of(BlockCoord coord) {
            return new Cell(coord.x(), coord.z());
        }
    }
}
