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

class SettlementPillarTests {
    private static final int LEVEL = 70;
    private static final String ORIGIN = "test-settlement-pillar.json";

    @Test
    void scan_PillarAboveFreeHeight_IsPillarAnyHeight() {
        Fake world = new Fake();
        world.lot(2, 2);
        world.put(2, LEVEL + PlotCells.FREE_HEIGHT + 5, 2, "minecraft:dirt");

        SettlementScan.Strays found = scan(world);

        assertEquals(
                List.of(new BlockCoord(2, LEVEL + PlotCells.FREE_HEIGHT + 5, 2)),
                found.pillars(),
                "Столб выше слоя свободной высоты обязан попасть в дефекты: иначе участок остаётся неровным");
        assertEquals(
                List.of(),
                found.mounds(),
                "Столб не дублируется бугром: одна колонка — один вид дефекта, иначе числа на вкладке врут");
    }

    @Test
    void scan_ForeignPillarAboveFreeHeight_IsPillar() {
        Fake world = new Fake();
        world.lot(2, 2);
        world.put(2, LEVEL + PlotCells.FREE_HEIGHT + 2, 2, "minecraft:cobblestone");

        SettlementScan.Strays found = scan(world);

        assertEquals(
                1,
                found.pillars().size(),
                "Посторонний блок выше свободного слоя — столб, а не лишний блок на земле: " + found.pillars());
    }

    private static SettlementScan.Strays scan(Fake world) {
        return SettlementScan.scan(ColumnMask.rect(0, 0, 6, 6).cells(), LEVEL, rules(), world);
    }

    private static BlockProtection rules() {
        String json = "{\"deny\": [\"bedrock\"], \"natural\": [\"stone\", \"dirt\", \"short_grass\"], "
                + "\"strays\": [\"*_fence\", \"cobblestone\"], \"buildingThreshold\": 6, "
                + "\"maxUnprotectVolume\": 4096}";
        return ProtectionConfig.parse(json, ORIGIN);
    }

    private static final class Fake implements SettlementScan.World {
        private final Map<BlockCoord, String> blocks = new HashMap<>();
        private final Set<ColumnMask.Cell> lots = new HashSet<>();

        void put(int x, int y, int z, String id) {
            blocks.put(new BlockCoord(x, y, z), id);
        }

        void lot(int x, int z) {
            lots.add(new ColumnMask.Cell(x, z));
        }

        @Override
        public boolean loaded(int x, int z) {
            return true;
        }

        @Override
        public boolean open(int x, int y, int z) {
            return "minecraft:air".equals(blockId(x, y, z));
        }

        @Override
        public String blockId(int x, int y, int z) {
            String known = blocks.get(new BlockCoord(x, y, z));
            return known != null ? known : y < LEVEL ? "minecraft:stone" : "minecraft:air";
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
            return false;
        }

        @Override
        public OptionalInt lotLevel(int x, int z) {
            return lots.contains(new ColumnMask.Cell(x, z)) ? OptionalInt.of(LEVEL) : OptionalInt.empty();
        }

        @Override
        public OptionalInt dugTop(int x, int z) {
            return OptionalInt.empty();
        }

        @Override
        public boolean playerPlaced(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean spiritMarked(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean registered(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean plant(String blockId) {
            return false;
        }
    }
}
