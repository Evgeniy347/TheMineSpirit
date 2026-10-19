package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.PlacedBlockCodec.ChunkEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlacedBlockCodecTests {
    @Test
    void encode_EmptySet_GivesNoChunks() {
        assertEquals(
                List.of(),
                PlacedBlockCodec.encode(new PlacedBlockSet()),
                "Пустой набор не пишет ни одного чанка: пустые чанки раздули бы сохранение");
    }

    @Test
    void encode_TwoChunks_GroupsPositionsByChunkInInsertionOrder() {
        PlacedBlockSet set = new PlacedBlockSet();
        set.add(new BlockCoord(17, 64, 3));
        set.add(new BlockCoord(1, 64, 1));
        set.add(new BlockCoord(18, 65, 4));

        List<ChunkEntry> entries = PlacedBlockCodec.encode(set);

        assertEquals(2, entries.size(), "Позиции разложены по двум чанкам");
        assertEquals(0, entries.get(0).chunkX(), "Чанки идут по возрастанию ключа: сохранение детерминировано");
        assertArrayEquals(
                new long[] {PlacedBlockSet.pack(new BlockCoord(1, 64, 1))},
                entries.get(0).positions(),
                "В чанке (0;0) одна позиция");
        assertEquals(1, entries.get(1).chunkX(), "Второй чанк — x = 1");
        assertArrayEquals(
                new long[] {
                    PlacedBlockSet.pack(new BlockCoord(17, 64, 3)), PlacedBlockSet.pack(new BlockCoord(18, 65, 4))
                },
                entries.get(1).positions(),
                "Порядок добавления сохранён: после загрузки вытесняются самые старые");
    }

    @Test
    void decode_EncodedSet_RoundTripsWithOrderAndOverflowMark() {
        PlacedBlockSet set = new PlacedBlockSet();
        for (int i = 0; i < 4100; i++) {
            set.add(new BlockCoord(i % 16, i / 256, (i / 16) % 16));
        }
        set.add(new BlockCoord(-40, -10, 500));

        PlacedBlockSet restored = PlacedBlockCodec.decode(PlacedBlockCodec.encode(set));

        assertEquals(set, restored, "Набор после кодека равен исходному: позиции, порядок и признак переполнения");
        assertTrue(
                PlacedBlockCodec.encode(restored).stream()
                        .filter(entry -> entry.chunkX() == 0 && entry.chunkZ() == 0)
                        .findFirst()
                        .orElseThrow()
                        .overflowed(),
                "Признак переполнения чанка пережил сохранение");
        assertTrue(
                restored.contains(new BlockCoord(-40, -10, 500)), "Позиция в отрицательном чанке пережила сохранение");
    }

    @Test
    void decode_RestoredSet_KeepsEvictingOldestFirst() {
        PlacedBlockSet set = new PlacedBlockSet();
        for (int i = 0; i < 4096; i++) {
            set.add(new BlockCoord(i % 16, i / 256, (i / 16) % 16));
        }
        PlacedBlockSet restored = PlacedBlockCodec.decode(PlacedBlockCodec.encode(set));

        restored.add(new BlockCoord(0, 20, 0));

        assertFalse(
                restored.contains(new BlockCoord(0, 0, 0)),
                "После загрузки вытесняется самая старая позиция, как и до сохранения");
        assertTrue(restored.contains(new BlockCoord(1, 0, 0)), "Вторая по возрасту осталась");
    }

    @Test
    void decode_PositionOutsideItsChunk_ThrowsWithPosition() {
        long foreign = PlacedBlockSet.pack(new BlockCoord(40, 64, 0));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PlacedBlockCodec.decode(List.of(new ChunkEntry(0, 0, false, new long[] {foreign}))),
                "позиция чужого чанка — повреждённое сохранение");

        assertTrue(
                error.getMessage().contains("вне чанка 0 0")
                        && error.getMessage().contains("x=40"),
                "Ошибка называет позицию и чанк. Фактическое: " + error.getMessage());
    }

    @Test
    void decode_DuplicateChunk_Throws() {
        long a = PlacedBlockSet.pack(new BlockCoord(1, 64, 1));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PlacedBlockCodec.decode(List.of(
                        new ChunkEntry(0, 0, false, new long[] {a}), new ChunkEntry(0, 0, false, new long[] {a}))),
                "один чанк в двух записях — повреждённое сохранение");

        assertTrue(
                error.getMessage().contains("записан дважды"),
                "Ошибка называет дубль чанка. Фактическое: " + error.getMessage());
    }

    @Test
    void decode_DuplicatePositionInChunk_Throws() {
        long a = PlacedBlockSet.pack(new BlockCoord(1, 64, 1));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PlacedBlockCodec.decode(List.of(new ChunkEntry(0, 0, false, new long[] {a, a}))),
                "одна позиция дважды — повреждённое сохранение");

        assertTrue(
                error.getMessage().contains("записана в чанке дважды"),
                "Ошибка называет дубль позиции. Фактическое: " + error.getMessage());
    }

    @Test
    void decode_MoreThanLimitInChunk_Throws() {
        long[] tooMany = new long[4097];
        for (int i = 0; i < tooMany.length; i++) {
            tooMany[i] = PlacedBlockSet.pack(new BlockCoord(i % 16, i / 256, (i / 16) % 16));
        }

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PlacedBlockCodec.decode(List.of(new ChunkEntry(0, 0, false, tooMany))),
                "4097 позиций в чанке — выше предела 4096");

        assertTrue(
                error.getMessage().contains("записано 4097 позиций, предел 4096"),
                "Ошибка называет число и предел. Фактическое: " + error.getMessage());
    }

    @Test
    void decode_EmptyChunkEntry_Throws() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PlacedBlockCodec.decode(List.of(new ChunkEntry(2, 3, false, new long[0]))),
                "чанк без позиций не сохраняется");

        assertTrue(
                error.getMessage().contains("записан без позиций"),
                "Ошибка называет пустой чанк. Фактическое: " + error.getMessage());
    }
}
