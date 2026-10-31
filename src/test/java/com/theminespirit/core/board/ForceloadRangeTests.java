package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.function.LongPredicate;
import org.junit.jupiter.api.Test;

class ForceloadRangeTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode live() {
        try (InputStream in = ForceloadRangeTests.class.getResourceAsStream("/live/observe9-forceload.json")) {
            if (in == null) {
                throw new IllegalStateException("Нет фикстуры наблюдения 9: /live/observe9-forceload.json");
            }
            return MAPPER.readTree(in);
        } catch (IOException error) {
            throw new IllegalStateException("Фикстура наблюдения 9 не читается", error);
        }
    }

    private static LongPredicate forced(JsonNode root) {
        JsonNode range = root.path("forceload");
        int minX = range.path("minChunkX").asInt();
        int maxX = range.path("maxChunkX").asInt();
        int minZ = range.path("minChunkZ").asInt();
        int maxZ = range.path("maxChunkZ").asInt();
        return key -> {
            int chunkX = (int) key;
            int chunkZ = (int) (key >> 32);
            return chunkX >= minX && chunkX <= maxX && chunkZ >= minZ && chunkZ <= maxZ;
        };
    }

    private static BlockCoord point(JsonNode node) {
        return new BlockCoord(
                node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt());
    }

    @Test
    void liveObserve9_FrozenSpiritOutside_ReturnedToHomeInsideForceload() {
        JsonNode root = live();
        LongPredicate forced = forced(root);
        BlockCoord position = point(root.path("spirits").path("spirit-2").path("position"));
        BlockCoord home = point(root.path("home"));

        assertFalse(
                ForceloadRange.inside(position, forced),
                "Фикстура наблюдения 9: spirit-2 в " + position
                        + " вне forceload — именно там он замёрз на 1 ч 06 мин");

        Optional<BlockCoord> target =
                ForceloadRange.returnPoint(position, forced, home, ForceloadRange.DEFAULT_SEARCH_CHUNKS);

        assertEquals(Optional.of(home), target, "Свой дом внутри forceload — первая точка возврата духа");
        assertTrue(
                ForceloadRange.inside(home, forced), "Возврат обязан вести в помеченный чанк, иначе дух замрёт снова");
    }

    @Test
    void liveObserve9_SpiritInsideForceload_NotRescued() {
        JsonNode root = live();
        LongPredicate forced = forced(root);
        BlockCoord position = point(root.path("spirits").path("spirit-3").path("position"));

        assertTrue(ForceloadRange.inside(position, forced), "spirit-3 в " + position + " стоял в помеченном чанке");
        assertEquals(
                Optional.empty(),
                ForceloadRange.returnPoint(position, forced, position, ForceloadRange.DEFAULT_SEARCH_CHUNKS),
                "Духа в помеченном чанке не дёргают: возврат нужен только вышедшему за forceload");
    }

    @Test
    void nearestForcedChunk_HomeOutsideForceload_CenterInside() {
        JsonNode root = live();
        LongPredicate forced = forced(root);
        BlockCoord position = point(root.path("spirits").path("spirit-2").path("position"));

        Optional<BlockCoord> target =
                ForceloadRange.returnPoint(position, forced, position, ForceloadRange.DEFAULT_SEARCH_CHUNKS);

        assertTrue(target.isPresent(), "Даже без дома внутри поселения возврат обязан найти ближайший помеченный чанк");
        assertTrue(
                ForceloadRange.inside(target.get(), forced),
                "Точка возврата " + target.get() + " обязана лежать в forceload");
        assertEquals(
                new BlockCoord(-152, position.y(), -328),
                target.get(),
                "Ближайший помеченный чанк к застрявшему в чанке [-10,-20] — [-10,-21], его центр обязан стать точкой возврата");
    }
}
