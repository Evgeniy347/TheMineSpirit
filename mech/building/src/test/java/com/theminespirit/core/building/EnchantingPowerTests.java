package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EnchantingPowerTests {
    private static final String BOOKSHELF = "minecraft:bookshelf";
    private static final String TABLE = "minecraft:enchanting_table";

    private static Schematic load(String file) {
        String path = "data/theminespirit/schematics/" + file + SchematicTextParser.EXTENSION;
        return SchematicTextParser.parse(JsonResource.readClasspath(path), path).schematic();
    }

    private static BlockCoord onlyTable(Schematic schematic) {
        List<BlockCoord> tables = EnchantingPower.tables(schematic);
        assertEquals(1, tables.size(), "У постройки обязан быть ровно один стол чарования, чтобы считать полки");
        return tables.get(0);
    }

    private static Schematic withBlock(Schematic base, int x, int y, int z, String block) {
        List<Schematic.Entry> entries = new ArrayList<>(base.blocks());
        entries.removeIf(entry -> entry.dx() == x && entry.dy() == y && entry.dz() == z);
        entries.add(new Schematic.Entry(x, y, z, block));
        return new Schematic(base.sizeX(), base.sizeY(), base.sizeZ(), entries);
    }

    private static Schematic synthetic(int sx, int sy, int sz, Schematic.Entry... entries) {
        return new Schematic(sx, sy, sz, List.of(entries));
    }

    private static Schematic.Entry entry(int x, int y, int z, String block) {
        return new Schematic.Entry(x, y, z, block);
    }

    @Test
    void shelfCount_RealEnchantRoomV1_CountsFiveShelves() {
        Schematic schematic = load("enchant_room_v1");

        assertEquals(
                5,
                EnchantingPower.shelfCount(schematic, onlyTable(schematic)),
                "Чаровальня уровня 1 — скромная деревянная изба: пять рабочих полок за столом (10 ступеней), "
                        + "а не готовый максимум с первого уровня");
    }

    @Test
    void level_RealEnchantRoomV1_IsTen() {
        Schematic schematic = load("enchant_room_v1");

        assertEquals(
                10,
                EnchantingPower.level(schematic, onlyTable(schematic)),
                "5 рабочих полок дают 10 ступеней стола: уровень 1 растёт дальше, а не начинает с потолка");
    }

    @Test
    void shelfCount_MiddleCellFilled_InvalidatesShelf() {
        Schematic schematic = load("enchant_room_v1");

        Schematic blocked = withBlock(schematic, 2, 1, 2, "minecraft:stone_bricks");

        assertEquals(
                4,
                EnchantingPower.shelfCount(blocked, onlyTable(blocked)),
                "Блок в клетке между столом и полкой лишает её силы: ванильный стол ищет там передатчик, "
                        + "а не сплошной блок");
    }

    @Test
    void shelfCount_ShelfThreeBlocksAway_AddsNoPower() {
        Schematic schematic = load("enchant_room_v1");

        Schematic far = withBlock(schematic, 3, 1, 0, BOOKSHELF);

        assertEquals(
                5,
                EnchantingPower.shelfCount(far, onlyTable(far)),
                "Полка дальше двух блоков от стола силы не даёт: расстояние задаёт само правило, а не число полок");
    }

    @Test
    void shelfCount_ShelfOneBlockAbove_Counts() {
        Schematic schematic = synthetic(5, 5, 5, entry(3, 1, 3, TABLE), entry(1, 2, 3, BOOKSHELF));

        assertEquals(
                1,
                EnchantingPower.shelfCount(schematic, new BlockCoord(3, 1, 3)),
                "Полка на блок выше стола рабочий ряд: ванильные смещения допускают высоту стола и на блок выше");
    }

    @Test
    void level_MoreThanFifteenShelves_CapsAtThirty() {
        List<Schematic.Entry> entries = new ArrayList<>();
        entries.add(entry(3, 1, 3, TABLE));
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    entries.add(entry(3 + dx, 1, 3 + dz, BOOKSHELF));
                }
            }
        }
        Schematic full = new Schematic(7, 4, 7, entries);

        assertEquals(
                16,
                EnchantingPower.shelfCount(full, new BlockCoord(3, 1, 3)),
                "Полное кольцо 5×5 без прохода — 16 рабочих полок: этот запас нужен, чтобы проверить предел");
        assertEquals(
                30,
                EnchantingPower.level(full, new BlockCoord(3, 1, 3)),
                "Больше 15 полок уровень не поднимают: высшая ступень стола — 30");
    }

    @Test
    void tables_RealEnchantRooms_HaveOneTableEach() {
        for (String file : List.of("enchant_room_v1", "enchant_room_v2", "enchant_room_v3")) {
            assertEquals(
                    1,
                    EnchantingPower.tables(load(file)).size(),
                    "У " + file + " обязан быть ровно один стол чарования: уровень растит полки вокруг него, "
                            + "а лишний стол без своего кольца зачаровывал бы слабее");
        }
    }

    @Test
    void growth_RealEnchantRooms_RiseInShelvesPowerCeilingWindowsAndEntrance() {
        Schematic one = load("enchant_room_v1");
        Schematic two = load("enchant_room_v2");
        Schematic three = load("enchant_room_v3");
        List<Schematic> rooms = List.of(one, two, three);

        assertEquals(
                List.of(5, 10, 15),
                rooms.stream()
                        .map(room -> EnchantingPower.shelfCount(room, onlyTable(room)))
                        .toList(),
                "Полки растут по уровню: 5 → 10 → 15 вокруг стола (15 — ванильный максимум зачарования)");
        assertEquals(
                List.of(10, 20, 30),
                rooms.stream().map(EnchantingPowerTests::levelOf).toList(),
                "Ступени стола растут с полками: 10 → 20 → 30, и только уровень 3 даёт максимум");
        assertEquals(
                List.of(3, 4, 5),
                rooms.stream().map(EnchantingPowerTests::ceilingHeight).toList(),
                "Чем выше уровень, тем выше потолок: 3 → 4 → 5 (решение владельца 19.10)");
        assertEquals(
                List.of(1, 2, 4),
                rooms.stream().map(EnchantingPowerTests::largestWindow).toList(),
                "Окна растут с уровнем: 1 → 2 → 4 клетки стекла в самом большом окне");
        assertEquals(
                List.of(1, 2, 4),
                rooms.stream().map(EnchantingPowerTests::lowerDoors).toList(),
                "Вход растёт с уровнем: одна створка → двойная дверь → портал в четыре створки");
        assertEquals(
                List.of(56, 110, 180),
                rooms.stream().map(EnchantingPowerTests::bodyCells).toList(),
                "Зал растёт по площади: 7×8 → 11×10 → 15×12 клеток пола, а не та же коробка");
        List<Integer> decor =
                rooms.stream().map(EnchantingPowerTests::decorBlocks).toList();
        assertTrue(
                decor.get(0) < decor.get(1) && decor.get(1) < decor.get(2),
                "Декор растёт по уровню: полки, ковры, свечи, кафедра, фонари и цветы — " + decor);
    }

    @Test
    void upgrade_EnchantRoomLevels_KeepPreviousWorkInPlace() {
        assertEquals(
                List.of(),
                movedParts(load("enchant_room_v1"), load("enchant_room_v2")),
                "Апгрейд 1→2 строится на месте: стол и пять полок уровня 1 обязаны остаться там же, "
                        + "иначе апгрейд ломает рабочий стол");
        assertEquals(
                List.of(),
                movedParts(load("enchant_room_v2"), load("enchant_room_v3")),
                "Апгрейд 2→3 строится на месте: десять полок и стол уровня 2 обязаны остаться там же, "
                        + "третье кольцо лишь дополняет их до пятнадцати");
    }

    private static int levelOf(Schematic schematic) {
        return EnchantingPower.level(schematic, onlyTable(schematic));
    }

    private static List<String> movedParts(Schematic first, Schematic second) {
        List<String> moved = new ArrayList<>();
        for (Schematic.Entry entry : first.blocks()) {
            if (!BOOKSHELF.equals(entry.block()) && !TABLE.equals(entry.block())) {
                continue;
            }
            boolean kept = second.blocks().stream()
                    .anyMatch(other -> other.dx() == entry.dx()
                            && other.dy() == entry.dy()
                            && other.dz() == entry.dz()
                            && other.block().equals(entry.block()));
            if (!kept) {
                moved.add(entry.block() + "@" + entry.dx() + "," + entry.dy() + "," + entry.dz());
            }
        }
        return moved;
    }

    private static int ceilingHeight(Schematic schematic) {
        BlockCoord table = onlyTable(schematic);
        for (int y = schematic.sizeY() - 1; y > table.y(); y--) {
            int level = y;
            boolean covered = schematic.blocks().stream()
                    .anyMatch(entry -> entry.dx() == table.x() && entry.dy() == level && entry.dz() == table.z());
            if (covered) {
                return y - 1;
            }
        }
        return -1;
    }

    private static int largestWindow(Schematic schematic) {
        Map<BlockCoord, String> grid = new HashMap<>();
        for (Schematic.Entry entry : schematic.blocks()) {
            grid.put(new BlockCoord(entry.dx(), entry.dy(), entry.dz()), entry.block());
        }
        Set<BlockCoord> seen = new HashSet<>();
        int largest = 0;
        for (Map.Entry<BlockCoord, String> entry : grid.entrySet()) {
            if (!SchematicBlocks.isWindow(entry.getValue()) || !seen.add(entry.getKey())) {
                continue;
            }
            Deque<BlockCoord> queue = new ArrayDeque<>();
            queue.add(entry.getKey());
            int size = 0;
            while (!queue.isEmpty()) {
                BlockCoord cell = queue.poll();
                size++;
                for (BlockCoord next : List.of(
                        new BlockCoord(cell.x() + 1, cell.y(), cell.z()),
                        new BlockCoord(cell.x() - 1, cell.y(), cell.z()),
                        new BlockCoord(cell.x(), cell.y() + 1, cell.z()),
                        new BlockCoord(cell.x(), cell.y() - 1, cell.z()),
                        new BlockCoord(cell.x(), cell.y(), cell.z() + 1),
                        new BlockCoord(cell.x(), cell.y(), cell.z() - 1))) {
                    String block = grid.get(next);
                    if (block != null && SchematicBlocks.isWindow(block) && seen.add(next)) {
                        queue.add(next);
                    }
                }
            }
            largest = Math.max(largest, size);
        }
        return largest;
    }

    private static int lowerDoors(Schematic schematic) {
        return (int) schematic.blocks().stream()
                .filter(entry -> entry.dy() == 1 && SchematicBlocks.isDoor(entry.block()))
                .count();
    }

    private static int bodyCells(Schematic schematic) {
        return schematic.sizeX() * (schematic.sizeZ() - 1);
    }

    private static int decorBlocks(Schematic schematic) {
        Map<BlockCoord, String> grid = new HashMap<>();
        for (Schematic.Entry entry : schematic.blocks()) {
            grid.put(new BlockCoord(entry.dx(), entry.dy(), entry.dz()), entry.block());
        }
        int decor = 0;
        for (Schematic.Entry entry : schematic.blocks()) {
            BlockCoord cell = new BlockCoord(entry.dx(), entry.dy(), entry.dz());
            if (SchematicLevelRules.isDecor(cell, entry.block(), schematic, grid)) {
                decor++;
            }
        }
        return decor;
    }
}
