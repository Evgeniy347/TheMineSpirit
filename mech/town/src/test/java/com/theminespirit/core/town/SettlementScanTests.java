package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.ProtectionConfig;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SettlementScanTests {
    private static final int LEVEL = 70;
    private static final String ORIGIN = "test-settlement-scan.json";

    @Test
    void scan_FenceOutsidePlanInsideFence_IsStray() {
        Fake world = new Fake();
        world.put(1, LEVEL, 1, "minecraft:oak_fence");
        world.put(2, LEVEL, 1, "minecraft:oak_fence");

        SettlementScan.Strays found = scan(world, ColumnMask.rect(0, 0, 4, 4));

        assertEquals(
                2,
                found.strays().size(),
                "забор вне клеток плана внутри ограды обязан попасть в лишнее, даже если стоит на земле");
        assertEquals("minecraft:oak_fence", found.strays().get(0).blockId(), "тип лишнего называется по блоку");
    }

    @Test
    void scan_PlayerBlockOrPlanCell_IsNotStray() {
        Fake world = new Fake();
        world.put(1, LEVEL, 1, "minecraft:oak_fence");
        world.player.add(new BlockCoord(1, LEVEL, 1));
        world.put(2, LEVEL, 1, "minecraft:cobblestone");
        world.planned.add(new BlockCoord(2, LEVEL, 1));

        SettlementScan.Strays found = scan(world, ColumnMask.rect(0, 0, 4, 4));

        assertEquals(List.of(), found.strays(), "блок игрока и клетка плана не считаются лишними");
    }

    @Test
    void scan_NaturalDipOutsideLot_IsNotPit() {
        Fake world = new Fake();
        world.hole(4, 3);

        SettlementScan.Strays found = scan(world, ColumnMask.rect(0, 0, 6, 6));

        assertEquals(Map.of(), found.pits(), "природная впадина вне лота — не яма и не работа");
    }

    @Test
    void scan_NaturalHillOutsideLot_IsNaturalNotMound() {
        Fake world = new Fake();
        world.put(2, LEVEL, 2, "minecraft:dirt");
        world.put(2, LEVEL + 1, 2, "minecraft:dirt");

        SettlementScan.Strays found = scan(world, ColumnMask.rect(0, 0, 6, 6));

        assertEquals(List.of(), found.mounds(), "природный холм вне лота — не насыпь и не работа");
        assertEquals(2, found.natural(), "природный склон помечается natural, чтобы наблюдатель видел не работу");
    }

    @Test
    void scan_MoundOnLot_IsMound() {
        Fake world = new Fake();
        world.lot(2, 2);
        world.put(2, LEVEL, 2, "minecraft:dirt");

        SettlementScan.Strays found = scan(world, ColumnMask.rect(0, 0, 6, 6));

        assertEquals(
                List.of(new BlockCoord(2, LEVEL, 2)),
                found.mounds(),
                "земля выше отметки лота — насыпь на лоте, её духи срезают");
    }

    @Test
    void scan_SpiritDugPitOutsideLot_IsPit() {
        Fake world = new Fake();
        world.hole(4, 3);
        world.dug(4, LEVEL - 1, 3);

        SettlementScan.Strays found = scan(world, ColumnMask.rect(0, 0, 6, 6));

        assertEquals(
                Map.of(new BlockCoord(4, LEVEL - 1, 3), 3),
                found.pits(),
                "яма от добычи духа вне лота — явный след по учёту изменений, её засыпают");
    }

    @Test
    void scan_DugPitOnPlannedCell_IsNotPit() {
        Fake world = new Fake();
        world.hole(4, 3);
        world.dug(4, LEVEL - 1, 3);
        world.planned.add(new BlockCoord(4, LEVEL - 1, 3));

        SettlementScan.Strays found = scan(world, ColumnMask.rect(0, 0, 6, 6));

        assertEquals(Map.of(), found.pits(), "клетка плана (шахта, спуск) — не яма для засыпки");
    }

    @Test
    void scan_PlantOnFreeLevel_IsNotMound() {
        Fake world = new Fake();
        world.put(2, LEVEL, 2, "minecraft:short_grass");
        world.plants.add("minecraft:short_grass");

        SettlementScan.Strays found = scan(world, ColumnMask.rect(0, 0, 6, 6));

        assertEquals(List.of(), found.mounds(), "трава на уровне площадки — не насыпь");
    }

    @Test
    void scan_UnloadedColumn_IsUnknownNotAir() {
        Fake world = new Fake();
        world.unloaded.add(new ColumnMask.Cell(1, 1));

        SettlementScan.Strays found = scan(world, ColumnMask.rect(0, 0, 3, 3));

        assertEquals(1, found.unknown(), "незагруженный чанк — «неизвестно», а не «ровно»");
        assertEquals(Map.of(), found.pits(), "по незагруженной колонке ям не выдумываем");
    }

    @Test
    void scan_PlannedDepressionsAndSpiritTrace_AreNotPits() {
        Fake world = new Fake();
        world.lot(1, 1);
        world.hole(1, 1, 4);
        for (int y = LEVEL - 4; y <= LEVEL - 1; y++) {
            world.planned.add(new BlockCoord(1, y, 1));
        }
        world.lot(3, 1);
        world.hole(3, 1, 3);
        for (int y = LEVEL - 3; y <= LEVEL - 1; y++) {
            world.planned.add(new BlockCoord(3, y, 1));
        }
        world.lot(5, 1);
        world.put(5, LEVEL - 1, 1, "minecraft:water");
        world.planned.add(new BlockCoord(5, LEVEL - 1, 1));
        world.hole(7, 1, 3);
        world.dug(7, LEVEL - 1, 1);
        for (int y = LEVEL - 3; y <= LEVEL - 1; y++) {
            world.planned.add(new BlockCoord(7, y, 1));
        }
        world.hole(9, 1, 3);
        world.dug(9, LEVEL - 1, 1);
        world.spirit.add(new BlockCoord(9, LEVEL - 2, 1));

        SettlementScan.Strays found = scan(world, ColumnMask.rect(0, 0, 12, 3));

        assertEquals(
                Map.of(),
                found.pits(),
                "Шахта, колодец, пашня с водой и ступени спуска — законные углубления плана, "
                        + "а след духа возвращает restore_trace: засыпать их ямами нельзя");
        assertEquals(
                List.of(new BlockCoord(9, LEVEL - 2, 1)),
                found.traces(),
                "След духа остаётся следом, а не ямой: его возвращает отдельное задание");
    }

    @Test
    void scan_SpiritDugCell_IsTrace() {
        Fake world = new Fake();
        world.put(2, LEVEL - 2, 2, "minecraft:air");
        world.spirit.add(new BlockCoord(2, LEVEL - 2, 2));

        SettlementScan.Strays found = scan(world, ColumnMask.rect(0, 0, 6, 6));

        assertEquals(
                List.of(new BlockCoord(2, LEVEL - 2, 2)),
                found.traces(),
                "яма, выкопанная духом, — след: её возвращают как было");
    }

    private static SettlementScan.Strays scan(Fake world, ColumnMask area) {
        return SettlementScan.scan(area.cells(), LEVEL, rules(), world);
    }

    private static BlockProtection rules() {
        String json = "{\"deny\": [\"bedrock\"], \"natural\": [\"stone\", \"dirt\", \"short_grass\"], "
                + "\"strays\": [\"*_fence\", \"cobblestone\"], \"buildingThreshold\": 6, "
                + "\"maxUnprotectVolume\": 4096}";
        return ProtectionConfig.parse(json, ORIGIN);
    }

    private static final class Fake implements SettlementScan.World {
        private final Map<BlockCoord, String> blocks = new HashMap<>();
        private final Set<BlockCoord> planned = new HashSet<>();
        private final Set<BlockCoord> player = new HashSet<>();
        private final Set<BlockCoord> spirit = new HashSet<>();
        private final Set<ColumnMask.Cell> unloaded = new HashSet<>();
        private final Set<String> plants = new HashSet<>();
        private final Set<ColumnMask.Cell> lots = new HashSet<>();
        private final Map<ColumnMask.Cell, Integer> dug = new HashMap<>();

        void put(int x, int y, int z, String id) {
            blocks.put(new BlockCoord(x, y, z), id);
        }

        void hole(int x, int z) {
            hole(x, z, 3);
        }

        void hole(int x, int z, int depth) {
            for (int y = LEVEL - 1; y >= LEVEL - depth; y--) {
                put(x, y, z, "minecraft:air");
            }
        }

        void lot(int x, int z) {
            lots.add(new ColumnMask.Cell(x, z));
        }

        void dug(int x, int y, int z) {
            dug.merge(new ColumnMask.Cell(x, z), y, Math::max);
        }

        @Override
        public boolean loaded(int x, int z) {
            return !unloaded.contains(new ColumnMask.Cell(x, z));
        }

        @Override
        public boolean open(int x, int y, int z) {
            String id = blocks.getOrDefault(new BlockCoord(x, y, z), y < LEVEL ? "minecraft:stone" : "minecraft:air");
            return "minecraft:air".equals(id) || "minecraft:water".equals(id);
        }

        @Override
        public String blockId(int x, int y, int z) {
            return blocks.getOrDefault(new BlockCoord(x, y, z), y < LEVEL ? "minecraft:stone" : "minecraft:air");
        }

        @Override
        public int top(int x, int z) {
            return blocks.keySet().stream()
                    .filter(at -> at.x() == x && at.z() == z)
                    .mapToInt(BlockCoord::y)
                    .max()
                    .orElse(LEVEL - 1);
        }

        @Override
        public boolean planned(int x, int y, int z) {
            return planned.contains(new BlockCoord(x, y, z));
        }

        @Override
        public OptionalInt lotLevel(int x, int z) {
            return lots.contains(new ColumnMask.Cell(x, z)) ? OptionalInt.of(LEVEL) : OptionalInt.empty();
        }

        @Override
        public OptionalInt dugTop(int x, int z) {
            Integer top = dug.get(new ColumnMask.Cell(x, z));
            return top == null ? OptionalInt.empty() : OptionalInt.of(top);
        }

        @Override
        public boolean playerPlaced(int x, int y, int z) {
            return player.contains(new BlockCoord(x, y, z));
        }

        @Override
        public boolean spiritMarked(int x, int y, int z) {
            return spirit.contains(new BlockCoord(x, y, z));
        }

        @Override
        public boolean temporary(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean registered(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean plant(String blockId) {
            return plants.contains(blockId);
        }
    }
}
