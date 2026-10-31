package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.town.BuildingSeeds;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class A2HutProtectionLiveTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String AIR = "minecraft:air";
    private static final List<String> HUT =
            List.of("-169,71,-489", "-170,71,-489", "-168,71,-489", "-170,76,-488", "-169,76,-488", "-171,75,-488");

    private BlockProtection protection;
    private Map<String, String> blocks;

    @BeforeAll
    void load() throws IOException {
        protection = ProtectionConfig.load();
        blocks = read();
    }

    @Test
    void liveHut_FurnacePorchAndRoof_AreProtectedFromTidy() {
        for (String at : HUT) {
            String blockId = blocks.get(at);
            List<String> neighbours = neighbours(at);
            boolean refused = SettlementBreak.decide(protection, facts(blockId), () -> neighbours)
                    instanceof BlockProtection.Refused;

            assertTrue(
                    refused || BuildingSeeds.adjacentToStructure(neighbours),
                    "Живая постройка игрока в " + at + " (" + blockId + "): уборка обязана оставить её — "
                            + "в observe14 убрали 2 oak_stairs из крыльца и хижина просела 99→97%, а печь "
                            + "висела защищённой и не давала закрыть задание");
        }
    }

    @Test
    void liveOpenGround_LoneLeftover_StaysRemovable() {
        BlockProtection.Decision decision =
                SettlementBreak.decide(protection, facts("minecraft:oak_planks"), () -> neighbours("-100,70,-400"));

        assertEquals(
                new BlockProtection.Allowed(),
                decision,
                "Одинокая доска в чистом поле не постройка: висящий остаток обязан убираться, иначе уборка "
                        + "перестаёт чистить мир");
        assertTrue(
                !BuildingSeeds.adjacentToStructure(neighbours("-100,70,-400")),
                "Рядом с одинокой доской нет станции/сундука: её нельзя записывать в чужую постройку");
    }

    private SettlementBreak.Facts facts(String blockId) {
        return new SettlementBreak.Facts(SettlementBreak.Origin.UNKNOWN, true, false, blockId, false, false);
    }

    private List<String> neighbours(String at) {
        String[] parts = at.split(",", -1);
        int x = Integer.parseInt(parts[0]);
        int y = Integer.parseInt(parts[1]);
        int z = Integer.parseInt(parts[2]);
        List<String> ids = new ArrayList<>(BlockProtection.NEIGHBOUR_COUNT);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    ids.add(blocks.getOrDefault((x + dx) + "," + (y + dy) + "," + (z + dz), AIR));
                }
            }
        }
        return ids;
    }

    private static Map<String, String> read() throws IOException {
        try (InputStream stream =
                A2HutProtectionLiveTests.class.getResourceAsStream("/live/n24b-hut-plan/hut-blocks.json")) {
            assertTrue(stream != null, "Живая фикстура блоков хижины не найдена: тест без неё слеп");
            JsonNode root = MAPPER.readTree(stream);
            Map<String, String> values = new LinkedHashMap<>();
            root.fields()
                    .forEachRemaining(
                            entry -> values.put(entry.getKey(), entry.getValue().asText()));
            return Map.copyOf(values);
        }
    }
}
