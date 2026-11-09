package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.ProtectionConfig;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SettlementTemporaryTraceTests {
    private static final int LEVEL = 70;
    private static final String ORIGIN = "test-settlement-temporary.json";

    @Test
    void scan_SpiritPlacedDirt_IsStrayEvenThoughDirtIsNatural() {
        Fake world = new Fake();
        world.put(3, LEVEL, 1, "minecraft:dirt");
        world.temporary.add(new BlockCoord(3, LEVEL, 1));

        SettlementScan.Strays found =
                SettlementScan.scan(ColumnMask.rect(0, 0, 5, 3).cells(), LEVEL, rules(), world);

        assertEquals(
                1,
                found.strays().size(),
                "Земля, поставленная духом вне плана, — след перемещения и убирается, хотя природная земля "
                        + "обычно не лишняя");
        assertEquals("minecraft:dirt", found.strays().get(0).blockId(), "Тип лишнего называет блок");
    }

    @Test
    void scan_HighBridgeBlock_IsStray() {
        Fake world = new Fake();
        world.put(2, LEVEL + 8, 2, "minecraft:cobblestone");

        SettlementScan.Strays found =
                SettlementScan.scan(ColumnMask.rect(0, 0, 5, 5).cells(), LEVEL, rules(), world);

        assertEquals(
                1,
                found.strays().size(),
                "Мост или столб духа на высоте 8 над лотом — тоже лишнее: скан обязан видеть его, "
                        + "а не обрываться на четырёх блоках над землёй");
        assertEquals(
                "minecraft:cobblestone",
                found.strays().get(0).blockId(),
                "Тип лишнего называет блок: по нему выбирают материал и снос");
    }

    private static BlockProtection rules() {
        String json = "{\"deny\": [\"bedrock\"], \"natural\": [\"stone\", \"dirt\", \"short_grass\"], "
                + "\"strays\": [\"*_fence\", \"cobblestone\"], \"buildingThreshold\": 6, "
                + "\"maxUnprotectVolume\": 4096}";
        return ProtectionConfig.parse(json, ORIGIN);
    }

    private static final class Fake implements SettlementScan.World {
        private final Map<BlockCoord, String> blocks = new HashMap<>();
        private final Set<BlockCoord> temporary = new HashSet<>();

        void put(int x, int y, int z, String id) {
            blocks.put(new BlockCoord(x, y, z), id);
        }

        @Override
        public boolean loaded(int x, int z) {
            return true;
        }

        @Override
        public boolean open(int x, int y, int z) {
            String id = blockId(x, y, z);
            return "minecraft:air".equals(id) || "minecraft:water".equals(id);
        }

        @Override
        public String blockId(int x, int y, int z) {
            return blocks.getOrDefault(new BlockCoord(x, y, z), y < LEVEL ? "minecraft:stone" : "minecraft:air");
        }

        @Override
        public boolean planned(int x, int y, int z) {
            return false;
        }

        @Override
        public OptionalInt lotLevel(int x, int z) {
            return OptionalInt.empty();
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
        public boolean temporary(int x, int y, int z) {
            return temporary.contains(new BlockCoord(x, y, z));
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
