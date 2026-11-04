package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.ProtectionConfig;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    void scan_PitOutsideStagePlatform_IsFound() {
        Fake world = new Fake();
        world.hole(4, 3);

        SettlementScan.Strays found = scan(world, ColumnMask.rect(0, 0, 6, 6));

        assertTrue(found.pits().containsKey(new BlockCoord(4, LEVEL - 1, 3)), "яма вне площадки этапа видна сканеру");
        assertEquals(3, found.pits().get(new BlockCoord(4, LEVEL - 1, 3)), "глубина ямы считается вниз");
    }

    @Test
    void scan_MoundOfDirtOutsidePlan_IsFound() {
        Fake world = new Fake();
        world.put(2, LEVEL, 2, "minecraft:dirt");

        SettlementScan.Strays found = scan(world, ColumnMask.rect(0, 0, 6, 6));

        assertEquals(List.of(new BlockCoord(2, LEVEL, 2)), found.mounds(), "насыпь вне плана входит в рельеф");
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

        void put(int x, int y, int z, String id) {
            blocks.put(new BlockCoord(x, y, z), id);
        }

        void hole(int x, int z) {
            for (int y = LEVEL - 1; y >= LEVEL - 3; y--) {
                put(x, y, z, "minecraft:air");
            }
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
    }
}
