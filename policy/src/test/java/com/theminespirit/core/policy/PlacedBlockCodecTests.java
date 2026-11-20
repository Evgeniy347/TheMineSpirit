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
                "Пустой набор не пишет ни одного чанка: пустые чанки раздули бы сохранение и переполнили лимиты. "
                        + "При смене encode проверь пустой набор");
    }

    @Test
    void encode_TwoChunks_GroupsPositionsByChunkInInsertionOrder() {
        PlacedBlockSet set = new PlacedBlockSet();
        set.add(new BlockCoord(17, 64, 3));
        set.add(new BlockCoord(1, 64, 1));
        set.add(new BlockCoord(18, 65, 4));

        List<ChunkEntry> entries = PlacedBlockCodec.encode(set);

        assertEquals(
                2,
                entries.size(),
                "Позиции разложены по двум чанкам, иначе кодек пишет лишние записи и раздувает файл. "
                        + "При смене группировки проверь координаты на границе x=16");
        assertEquals(
                0,
                entries.get(0).chunkX(),
                "Чанки идут по возрастанию ключа: сохранение детерминировано, иначе файл меняется от запуска к "
                        + "запуску. При смене сортировки проверь порядок записей");
        assertArrayEquals(
                new long[] {PlacedBlockSet.pack(new BlockCoord(1, 64, 1))},
                entries.get(0).positions(),
                "В чанке (0;0) одна позиция, иначе смешиваются координаты соседних чанков. "
                        + "При смене раскладки проверь границу чанка x=16");
        assertEquals(
                1,
                entries.get(1).chunkX(),
                "Второй чанк — x = 1, иначе позиции из x=17 попадают в чужую запись. "
                        + "При смене ключа чанка проверь деление координаты на 16");
        assertArrayEquals(
                new long[] {
                    PlacedBlockSet.pack(new BlockCoord(17, 64, 3)), PlacedBlockSet.pack(new BlockCoord(18, 65, 4))
                },
                entries.get(1).positions(),
                "Порядок добавления сохранён: после загрузки вытесняются самые старые, иначе возраст блоков "
                        + "теряется. При смене encode проверь порядок позиций внутри чанка");
    }

    @Test
    void decode_EncodedSet_RoundTripsWithOrderAndOverflowMark() {
        PlacedBlockSet set = new PlacedBlockSet();
        for (int i = 0; i < 4100; i++) {
            set.add(new BlockCoord(i % 16, i / 256, (i / 16) % 16));
        }
        set.add(new BlockCoord(-40, -10, 500));

        PlacedBlockSet restored = PlacedBlockCodec.decode(PlacedBlockCodec.encode(set));

        assertEquals(
                set,
                restored,
                "Набор после кодека равен исходному: позиции, порядок и признак переполнения, иначе защита "
                        + "забывает блоки после перезапуска. При смене формата проверь переполненный чанк");
        assertTrue(
                PlacedBlockCodec.encode(restored).stream()
                        .filter(entry -> entry.chunkX() == 0 && entry.chunkZ() == 0)
                        .findFirst()
                        .orElseThrow()
                        .overflowed(),
                "Признак переполнения чанка пережил сохранение, иначе эвристика «здание» теряет подсказку о "
                        + "вытесненных блоках. При смене decode проверь флаг overflowed");
        assertTrue(
                restored.contains(new BlockCoord(-40, -10, 500)),
                "Позиция в отрицательном чанке пережила сохранение, иначе отрицательные координаты теряются "
                        + "при упаковке. При смене ключа чанка проверь отрицательное деление");
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
                "После загрузки вытесняется самая старая позиция, как и до сохранения, иначе порядок возраста "
                        + "теряется. При смене decode проверь порядок внутри чанка");
        assertTrue(
                restored.contains(new BlockCoord(1, 0, 0)),
                "Вторая по возрасту осталась, иначе переполнение вытесняет больше одной позиции. "
                        + "При смене очереди проверь соседнюю с самой старой");
    }

    @Test
    void decode_PositionOutsideItsChunk_ThrowsWithPosition() {
        long foreign = PlacedBlockSet.pack(new BlockCoord(40, 64, 0));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PlacedBlockCodec.decode(List.of(new ChunkEntry(0, 0, false, new long[] {foreign}))),
                "позиция чужого чанка — повреждённое сохранение, иначе защита ставит блок в неверную клетку. "
                        + "При смене decode проверь сверку чанка у каждой позиции");

        assertTrue(
                error.getMessage().contains("вне чанка 0 0")
                        && error.getMessage().contains("x=40"),
                "Ошибка называет позицию и чанк, иначе повреждённый файл не починить. Фактическое: "
                        + error.getMessage() + ". При смене текста проверь x=40 и «вне чанка 0 0»");
    }

    @Test
    void decode_DuplicateChunk_Throws() {
        long a = PlacedBlockSet.pack(new BlockCoord(1, 64, 1));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PlacedBlockCodec.decode(List.of(
                        new ChunkEntry(0, 0, false, new long[] {a}), new ChunkEntry(0, 0, false, new long[] {a}))),
                "один чанк в двух записях — повреждённое сохранение, иначе позиции дублируются и вытеснение "
                        + "ломается. При смене decode проверь повтор ключа чанка");

        assertTrue(
                error.getMessage().contains("записан дважды"),
                "Ошибка называет дубль чанка, иначе непонятно, какую запись удалять. Фактическое: " + error.getMessage()
                        + ". При смене текста проверь «записан дважды»");
    }

    @Test
    void decode_DuplicatePositionInChunk_Throws() {
        long a = PlacedBlockSet.pack(new BlockCoord(1, 64, 1));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PlacedBlockCodec.decode(List.of(new ChunkEntry(0, 0, false, new long[] {a, a}))),
                "одна позиция дважды — повреждённое сохранение, иначе набор считает блок дважды и врёт о размере. "
                        + "При смене decode проверь повтор позиции внутри чанка");

        assertTrue(
                error.getMessage().contains("записана в чанке дважды"),
                "Ошибка называет дубль позиции, иначе файл не починить. Фактическое: " + error.getMessage()
                        + ". При смене текста проверь «записана в чанке дважды»");
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
                "4097 позиций в чанке — выше предела 4096, иначе чанк переполняет сохранение и вытеснение "
                        + "выходит за рамки формата. При смене лимита проверь границу 4096 и 4097");

        assertTrue(
                error.getMessage().contains("записано 4097 позиций, предел 4096"),
                "Ошибка называет число и предел, иначе непонятно, сколько позиций лишние. Фактическое: "
                        + error.getMessage() + ". При смене текста проверь оба числа");
    }

    @Test
    void decode_EmptyChunkEntry_Throws() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PlacedBlockCodec.decode(List.of(new ChunkEntry(2, 3, false, new long[0]))),
                "чанк без позиций не сохраняется, иначе пустые записи раздувают файл и ломают лимит. "
                        + "При смене decode проверь пустой массив позиций");

        assertTrue(
                error.getMessage().contains("записан без позиций"),
                "Ошибка называет пустой чанк, иначе непонятно, какую запись убрать. Фактическое: " + error.getMessage()
                        + ". При смене текста проверь «записан без позиций»");
    }
}
