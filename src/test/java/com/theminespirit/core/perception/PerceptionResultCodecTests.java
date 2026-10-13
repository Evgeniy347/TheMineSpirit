package com.theminespirit.core.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.EntityRef;
import com.theminespirit.core.observation.ItemCount;
import com.theminespirit.core.observation.ScanRadius;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PerceptionResultCodecTests {
    private static final ScanRadius RADIUS = ScanRadius.of(8);

    @Test
    void limits_Constants_MatchAgreedNumbers() {
        assertEquals(200, PerceptionResultCodec.MAX_STRING_LENGTH,
                "лимит строки — 200 символов (H1 согласован): завышение растит JSON каждой задачи");
        assertEquals(32, PerceptionResultCodec.MAX_LIST_ITEMS,
                "лимит списка — 32 элемента (H1 согласован): больше стратег не читает");
        assertEquals(4096, PerceptionResultCodec.MAX_JSON_BYTES,
                "лимит объекта — 4 КиБ (H1 согласован): больше — перегрузка каждой задачи");
    }

    @Test
    void toMap_BlocksResult_RoundTripsWithoutLoss() {
        PerceptionResult result = PerceptionResult.blocks(RADIUS,
                List.of(new ItemCount("minecraft:oak_log", 12), new ItemCount("minecraft:dirt", 3)));

        Map<String, Object> map = PerceptionResultCodec.toMap(result);
        PerceptionResult back = PerceptionResultCodec.toPerceptionResult(map);

        assertEquals(result, back, "JSON-объект блоков обязан возвращаться без потерь: item, count и радиус");
        assertEquals("blocks", map.get("type"), "тип обязан называться в нижнем регистре");
        assertEquals(8, map.get("radius"), "радиус запроса обязан попасть в JSON");
    }

    @Test
    void toMap_EntitiesResult_RoundTripsWithoutLoss() {
        PerceptionResult result = PerceptionResult.entities(RADIUS,
                List.of(new EntityRef("minecraft:zombie", new BlockCoord(3, 64, -2), 4.5)));

        Map<String, Object> map = PerceptionResultCodec.toMap(result);
        PerceptionResult back = PerceptionResultCodec.toPerceptionResult(map);

        assertEquals(result, back, "JSON-объект сущностей обязан возвращаться без потерь: тип, позиция, дистанция");
        @SuppressWarnings("unchecked")
        Map<String, Object> entry = (Map<String, Object>) ((List<Object>) map.get("entities")).get(0);
        assertEquals(List.of(3, 64, -2), entry.get("pos"), "позиция обязана стать массивом [x,y,z]");
    }

    @Test
    void toMap_HazardsResult_RoundTripsWithoutLoss() {
        PerceptionResult result = PerceptionResult.hazards(RADIUS,
                List.of(new HazardRef(HazardType.LAVA, new BlockCoord(1, 60, 1), 2.25)));

        Map<String, Object> map = PerceptionResultCodec.toMap(result);
        PerceptionResult back = PerceptionResultCodec.toPerceptionResult(map);

        assertEquals(result, back, "JSON-объект опасностей обязан возвращаться без потерь: вид, позиция, дистанция");
    }

    @Test
    void toMap_Finding_RoundTripsWithoutLoss() {
        Finding finding = new Finding("locate_block",
                List.of(new Sighting(Sighting.BLOCK, "minecraft:diamond_ore", new BlockCoord(5, 12, 9),
                        "расстояние 12.3")));

        Map<String, Object> map = PerceptionResultCodec.toMap(finding);
        Finding back = PerceptionResultCodec.toFinding(map);

        assertEquals(finding, back, "JSON-объект находки обязан возвращаться без потерь: kind, label, pos, detail");
        assertEquals("located", map.get("type"), "locate_* обязан называться located");
        assertEquals("locate_block", map.get("script"), "находка обязана помнить скрипт, который её дал");
    }

    @Test
    void toMap_CellAndMemoryScripts_AreTypedCellAndMemory() {
        assertEquals("cell", PerceptionResultCodec.toMap(
                new Finding("look_at", List.of())).get("type"), "look_at — находка ячейки, тип cell");
        assertEquals("memory", PerceptionResultCodec.toMap(
                new Finding("recall", List.of())).get("type"), "recall — находка памяти, тип memory");
    }

    @Test
    void toMap_StringOverLimit_IsCutAndFlaggedTruncated() {
        String longLabel = "x".repeat(250);

        Map<String, Object> map = PerceptionResultCodec.toMap(
                new Finding("locate_block",
                        List.of(new Sighting(Sighting.BLOCK, longLabel, new BlockCoord(1, 2, 3), "дальше"))));

        assertEquals(200, ((String) labelOf(map)).length(),
                "строка длиннее 200 символов обязана обрезаться до лимита (H1)");
        assertEquals(Boolean.TRUE, map.get("truncated"), "обрезанная строка обязана нести truncated: true");
    }

    @Test
    void toMap_ListOverLimit_IsCutAndFlaggedTruncated() {
        List<Sighting> many = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            many.add(new Sighting(Sighting.BLOCK, "block_" + i, new BlockCoord(i, 1, 0), "расстояние " + i));
        }

        Map<String, Object> map = PerceptionResultCodec.toMap(new Finding("locate_block", many));

        assertEquals(32, sightingsOf(map).size(),
                "список длиннее 32 элементов обязана обрезаться до лимита (H1)");
        assertEquals(Boolean.TRUE, map.get("truncated"), "обрезанный список обязана нести truncated: true");
    }

    @Test
    void toMap_JsonOverByteLimit_ShrinksToFitAndFlagsTruncated() {
        List<Sighting> many = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            many.add(new Sighting(Sighting.BLOCK, "y".repeat(200), new BlockCoord(i, 1, 0),
                    "z".repeat(200)));
        }

        Map<String, Object> map = PerceptionResultCodec.toMap(new Finding("locate_block", many));

        byte[] json = toJson(map);
        assertTrue(json.length <= PerceptionResultCodec.MAX_JSON_BYTES,
                "JSON итога обязан укладываться в 4 КиБ (H1), фактически: " + json.length + " байт");
        assertEquals(Boolean.TRUE, map.get("truncated"), "обрезка по байтам обязана нести truncated: true");
    }

    @Test
    void toPerceptionResult_UnknownType_IsRejectedNamingType() {
        Map<String, Object> map = Map.of("type", "souls", "radius", 8);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PerceptionResultCodec.toPerceptionResult(map),
                "неизвестный тип обязан ронять разбор: тихий fallback спрятал бы опечатку в данных");

        assertTrue(error.getMessage().contains("souls"),
                "отказ обязан называть неизвестный тип: по сообщению видно, что пришло. Фактическое: "
                        + error.getMessage());
    }

    static Stream<Map<String, Object>> roundTripMaps() {
        return Stream.of(
                PerceptionResultCodec.toMap(PerceptionResult.blocks(RADIUS,
                        List.of(new ItemCount("minecraft:cobblestone", 64)))),
                PerceptionResultCodec.toMap(Finding.notFound(LocateType.TREE)));
    }

    @ParameterizedTest
    @MethodSource("roundTripMaps")
    void toMap_JsonBytesStayUnderLimit_ForSmallResults(Map<String, Object> map) {
        assertTrue(toJson(map).length <= PerceptionResultCodec.MAX_JSON_BYTES,
                "небольшой результат обязан помещаться в лимит без обрезки: " + toJson(map).length + " байт");
    }

    private static Object labelOf(Map<String, Object> map) {
        Map<?, ?> sighting = (Map<?, ?>) sightingsOf(map).get(0);
        return sighting.get("label");
    }

    private static List<?> sightingsOf(Map<String, Object> map) {
        return (List<?>) map.get("sightings");
    }

    private static byte[] toJson(Map<String, Object> map) {
        try {
            return new ObjectMapper().writeValueAsBytes(map);
        } catch (JsonProcessingException e) {
            throw new AssertionError("тестовый JSON обязан сериализоваться: " + e.getMessage(), e);
        }
    }
}
