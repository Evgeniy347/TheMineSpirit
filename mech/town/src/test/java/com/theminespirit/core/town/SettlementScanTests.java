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
    void scan_HighColumnAboveHead_IsHighNotOrdinaryStray() {
        Fake world = new Fake();
        world.put(2, LEVEL + 20, 2, "minecraft:cobblestone");

        SettlementScan.Strays found = scan(world, ColumnMask.rect(0, 0, 6, 6));

        assertEquals(
                List.of(new BlockCoord(2, LEVEL + 20, 2)),
                found.high().stream().map(cell -> cell.at()).toList(),
                "Столб на 20 выше земли обязан попасть в отдельный факт high_strays: с земли его не достать, "
                        + "а обычная расчистка ищет только нижние четыре клетки");
        assertEquals(List.of(), found.strays(), "Высокое не смешивается с обычным лишним");
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
            for (int y = LEVEL - 1; y >= LEVEL - 3; y--) {
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
            return "minecraft:air"
                    .equals(blocks.getOrDefault(
                            new BlockCoord(x, y, z), y < LEVEL ? "minecraft:stone" : "minecraft:air"));
        }

        @Override
        public String blockId(int x, int y, int z) {
            return blocks.getOrDefault(new BlockCoord(x, y, z), y < LEVEL ? "minecraft:stone" : "minecraft:air");
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
        public boolean registered(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean plant(String blockId) {
            return plants.contains(blockId);
        }

        @Override
        public int topSolid(int x, int z) {
            return blocks.entrySet().stream()
                    .filter(entry -> entry.getKey().x() == x && entry.getKey().z() == z)
                    .mapToInt(entry -> entry.getKey().y())
                    .max()
                    .orElse(LEVEL);
        }
    }
}
