package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.ProtectionConfig;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.StringTokenizer;
import org.junit.jupiter.api.Test;

class SettlementSnapshotTests {
    private static final int LEVEL = 70;
    private static final String ORIGIN = "test-settlement-snapshot.json";

    @Test
    void scan_LiveSnapshot_HillsOutsideLotsAreNatural() throws IOException {
        List<String> lines = snapshot();
        Fake world = new Fake();
        int[] lot = new int[4];
        List<String> rows = new ArrayList<>();
        for (String line : lines) {
            if (line.startsWith("#") || line.isBlank()) {
                continue;
            }
            if (line.startsWith("lot ")) {
                StringTokenizer parts = new StringTokenizer(line.substring("lot ".length()));
                for (int index = 0; index < 4 && parts.hasMoreTokens(); index++) {
                    lot[index] = Integer.parseInt(parts.nextToken());
                }
                continue;
            }
            rows.add(line);
        }
        assertEquals(32, rows.size(), "снимок рельефа — 32 строки колонок");
        for (int z = 0; z < rows.size(); z++) {
            String row = rows.get(z);
            assertEquals(32, row.length(), "снимок рельефа — 32 колонки в строке");
            for (int x = 0; x < row.length(); x++) {
                int rise = row.charAt(x) - '0';
                assertTrue(rise >= 0 && rise <= 2, "высота снимка — 0..2 над отметкой площадки");
                for (int y = LEVEL; y < LEVEL + rise; y++) {
                    world.put(x, y, z, "minecraft:dirt");
                }
            }
        }
        for (int x = lot[0]; x < lot[0] + lot[2]; x++) {
            for (int z = lot[1]; z < lot[1] + lot[3]; z++) {
                world.lots.add(new ColumnMask.Cell(x, z));
            }
        }

        SettlementScan.Strays found =
                SettlementScan.scan(ColumnMask.rect(0, 0, 31, 31).cells(), LEVEL, rules(), world);

        assertTrue(!found.mounds().isEmpty(), "снимок с неровным лотом обязан дать насыпи на лоте");
        assertTrue(
                found.mounds().size() * 10 <= found.natural(),
                "насыпей на порядок меньше природного: насыпи " + found.mounds().size() + ", природное "
                        + found.natural());
        assertTrue(
                found.mounds().stream()
                        .allMatch(at -> lot[0] <= at.x()
                                && at.x() < lot[0] + lot[2]
                                && lot[1] <= at.z()
                                && at.z() < lot[1] + lot[3]),
                "насыпи только на лоте, природный склон вне лота не работа: " + found.mounds());
        assertEquals(Map.of(), found.pits(), "природные впадины вне лота не становятся ямами");
    }

    private static List<String> snapshot() throws IOException {
        try (InputStream stream = SettlementSnapshotTests.class.getResourceAsStream("/live/worldmess/surface.txt")) {
            if (stream == null) {
                throw new IOException("Нет снимка живого мира '/live/worldmess/surface.txt': тест без фикстуры");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return reader.lines().toList();
            }
        }
    }

    private static BlockProtection rules() {
        String json = "{\"deny\": [\"bedrock\"], \"natural\": [\"stone\", \"dirt\", \"short_grass\"], "
                + "\"strays\": [\"*_fence\", \"cobblestone\"], \"buildingThreshold\": 6, "
                + "\"maxUnprotectVolume\": 4096}";
        return ProtectionConfig.parse(json, ORIGIN);
    }

    private static final class Fake implements SettlementScan.World {
        private final Map<String, String> blocks = new HashMap<>();
        private final Map<String, Integer> tops = new HashMap<>();
        private final Set<ColumnMask.Cell> lots = new HashSet<>();

        void put(int x, int y, int z, String id) {
            blocks.put(x + "," + y + "," + z, id);
            tops.merge(x + "," + z, y, Math::max);
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
            String known = blocks.get(x + "," + y + "," + z);
            return known != null ? known : y < LEVEL ? "minecraft:stone" : "minecraft:air";
        }

        @Override
        public int top(int x, int z) {
            return tops.getOrDefault(x + "," + z, LEVEL - 1);
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
        public boolean temporary(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean registered(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean plant(String blockId) {
            return "minecraft:short_grass".equals(blockId);
        }
    }
}
