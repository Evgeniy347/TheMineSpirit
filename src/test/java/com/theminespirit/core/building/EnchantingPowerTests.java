package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
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
    void shelfCount_RealEnchantRoomV1_CountsFifteenShelves() {
        Schematic schematic = load("enchant_room_v1");

        assertEquals(
                15,
                EnchantingPower.shelfCount(schematic, onlyTable(schematic)),
                "Чаровальня уровня 1 обязана давать ровно 15 рабочих полок: они стоят на расстоянии 2 от стола "
                        + "с пустой клеткой между");
    }

    @Test
    void level_RealEnchantRoomV1_IsThirty() {
        Schematic schematic = load("enchant_room_v1");

        assertEquals(
                30,
                EnchantingPower.level(schematic, onlyTable(schematic)),
                "15 рабочих полок дают высшую ступень стола — 30: геометрия постройки обязана это подтверждать");
    }

    @Test
    void shelfCount_MiddleCellFilled_InvalidatesShelf() {
        Schematic schematic = load("enchant_room_v1");

        Schematic blocked = withBlock(schematic, 2, 1, 2, "minecraft:stone_bricks");

        assertEquals(
                14,
                EnchantingPower.shelfCount(blocked, onlyTable(blocked)),
                "Блок в клетке между столом и полкой лишает её силы: ванильный стол ищет там передатчик, "
                        + "а не сплошной блок");
    }

    @Test
    void shelfCount_ShelfThreeBlocksAway_AddsNoPower() {
        Schematic schematic = load("enchant_room_v1");

        Schematic far = withBlock(schematic, 3, 1, 0, BOOKSHELF);

        assertEquals(
                15,
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
    void tables_RealEnchantRoomV2_FindsBothTables() {
        Schematic schematic = load("enchant_room_v2");

        assertEquals(
                2,
                EnchantingPower.tables(schematic).size(),
                "Уровень 2 несёт два стола чарования: запас под второй стол должен быть виден правилу");
    }

    @Test
    void level_RealEnchantRoomV2_EachTableThirty() {
        Schematic schematic = load("enchant_room_v2");

        List<Integer> levels = new ArrayList<>();
        for (BlockCoord table : EnchantingPower.tables(schematic)) {
            levels.add(EnchantingPower.level(schematic, table));
        }

        assertEquals(
                List.of(30, 30),
                levels,
                "Уровень 2 — два стола, у каждого 15 полок через клетку воздуха (владелец 19.10): второй стол без "
                        + "полного кольца зачаровывал бы слабее, и расширение теряло бы смысл");
    }

    @Test
    void upgrade_EnchantRoomV1ToV2_KeepsFirstRingInPlace() {
        Schematic first = load("enchant_room_v1");
        Schematic second = load("enchant_room_v2");

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

        assertEquals(
                List.of(),
                moved,
                "Апгрейд чаровальни 1→2 строится на месте (входы совпадают): кольцо уровня 1 обязано остаться "
                        + "там же, второе кольцо пристраивается рядом; иначе апгрейд ломает рабочий стол");
    }
}
