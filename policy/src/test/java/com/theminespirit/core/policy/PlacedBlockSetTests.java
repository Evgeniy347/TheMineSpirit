package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class PlacedBlockSetTests {
    private static BlockCoord inChunkZero(int index) {
        return new BlockCoord(index % 16, index / 256, (index / 16) % 16);
    }

    @Test
    void add_NewPosition_IsContainedAndCounted() {
        PlacedBlockSet set = new PlacedBlockSet();

        boolean added = set.add(new BlockCoord(5, 64, -3));

        assertTrue(
                added,
                "Новая позиция добавляется, иначе отметка ставится вхолостую и блок остаётся незащищённым. "
                        + "При смене add проверь пустой набор и новую координату");
        assertTrue(
                set.contains(new BlockCoord(5, 64, -3)),
                "Добавленная позиция найдена: иначе бревно игрока срубят, и постройка пропадёт. "
                        + "При смене contains проверь отрицательную координату z");
        assertEquals(
                1,
                set.size(),
                "Одна позиция в наборе, иначе счётчик расходится с содержимым и лимит чанка врёт. "
                        + "При смене add проверь размер после одного добавления");
    }

    @Test
    void add_SamePositionTwice_IsIdempotent() {
        PlacedBlockSet set = new PlacedBlockSet();
        set.add(new BlockCoord(5, 64, -3));

        boolean again = set.add(new BlockCoord(5, 64, -3));

        assertFalse(
                again,
                "Повторная отметка той же позиции не новая, иначе возраст блока сбрасывается и вытеснение "
                        + "выбирает не тот блок. При смене add проверь повторное добавление");
        assertEquals(
                1, set.size(), "Повтор не растит набор: иначе событие установки блока дважды съело бы лимит чанка");
    }

    @Test
    void remove_AddedPosition_IsGone() {
        PlacedBlockSet set = new PlacedBlockSet();
        set.add(new BlockCoord(5, 64, -3));

        boolean removed = set.remove(new BlockCoord(5, 64, -3));

        assertTrue(
                removed,
                "Добавленная позиция удаляется, иначе отметка держит чанк вечно и память течёт. "
                        + "При смене remove проверь возвращаемое значение");
        assertFalse(
                set.contains(new BlockCoord(5, 64, -3)),
                "Удалённая позиция не найдена: блок сломан, отметка не должна держать чанк, иначе память течёт. "
                        + "При смене remove проверь contains после удаления");
        assertEquals(
                0,
                set.size(),
                "Набор пуст после удаления единственной позиции, иначе размер не соответствует содержимому. "
                        + "При смене remove проверь счётчик размера");
    }

    @Test
    void remove_AbsentPosition_ReturnsFalse() {
        PlacedBlockSet set = new PlacedBlockSet();

        assertFalse(
                set.remove(new BlockCoord(1, 1, 1)),
                "Удаление отсутствующей позиции — не ошибка, а false: ломают и природные блоки, и исключение "
                        + "сорвало бы работу. При смене remove проверь пустой набор");
    }

    @Test
    void contains_NegativeCoordinates_AreDistinctFromPositive() {
        PlacedBlockSet set = new PlacedBlockSet();
        set.add(new BlockCoord(-1, -60, -17));

        assertTrue(
                set.contains(new BlockCoord(-1, -60, -17)),
                "Отрицательные координаты сохраняются точно, иначе блоки в отрицательных чанках путаются. "
                        + "При смене pack проверь отрицательные x, y и z");
        assertFalse(
                set.contains(new BlockCoord(1, 60, 17)),
                "Зеркальные положительные координаты не совпадают с отрицательными, иначе теряется знак при "
                        + "упаковке. При смене pack проверь пары (−1,1), (−60,60), (−17,17)");
        assertFalse(
                set.contains(new BlockCoord(15, -60, -17)),
                "Соседний чанк не совпадает, иначе contains читает чужой чанк по тому же индексу. "
                        + "При смене ключа проверь границу x=15 и x=-1");
    }

    @Test
    void add_MoreThanLimitInOneChunk_EvictsTheOldestAndMarksOverflow() {
        PlacedBlockSet set = new PlacedBlockSet();
        for (int i = 0; i < 4097; i++) {
            set.add(inChunkZero(i));
        }

        assertEquals(
                4096,
                set.size(),
                "В чанке держится не больше 4096 позиций, иначе сохранение переполняется и вытеснение не работает. "
                        + "При смене лимита проверь границу 4096 и 4097");
        assertFalse(
                set.contains(inChunkZero(0)),
                "Самая старая позиция вытеснена, иначе очередь не работает и набор растёт бесконечно. "
                        + "При смене вытеснения проверь первый добавленный блок");
        assertTrue(
                set.contains(inChunkZero(1)),
                "Вторая по возрасту осталась, иначе вытеснение удаляет лишние блоки. "
                        + "При смене очереди проверь соседнюю с самой старой позицию");
        assertTrue(
                set.contains(inChunkZero(4096)),
                "Новейшая позиция сохранена, иначе вытеснение стирает только что поставленный блок и защита "
                        + "слепнет. При смене вытеснения проверь последний добавленный блок");
        assertTrue(
                PlacedBlockCodec.encode(set).get(0).overflowed(),
                "Вытеснение помечает чанк: для него действует эвристика «здание», а не только набор, иначе "
                        + "потерянные блоки врут о постройке. При смене encode проверь флаг overflowed");
    }

    @Test
    void add_ExactlyLimitInOneChunk_DoesNotEvict() {
        PlacedBlockSet set = new PlacedBlockSet();
        for (int i = 0; i < 4096; i++) {
            set.add(inChunkZero(i));
        }

        assertEquals(
                4096,
                set.size(),
                "Ровно 4096 позиций помещаются целиком, иначе граница урезается на единицу. "
                        + "При смене лимита проверь ровно 4096 без вытеснения");
        assertTrue(
                set.contains(inChunkZero(0)),
                "Самая старая позиция при ровно 4096 ещё на месте: граница включительная, иначе первый блок "
                        + "теряется. При смене лимита проверь 4096-й блок");
        assertFalse(
                PlacedBlockCodec.encode(set).get(0).overflowed(),
                "Без вытеснения чанк не помечен, иначе эвристика «здание» срабатывает впустую. "
                        + "При смене флага проверь набор ровно на лимите");
    }

    @Test
    void remove_LastPositionOfOverflowedChunk_ClearsTheOverflowMark() {
        PlacedBlockSet set = new PlacedBlockSet();
        for (int i = 0; i < 4097; i++) {
            set.add(inChunkZero(i));
        }
        for (int i = 1; i < 4097; i++) {
            set.remove(inChunkZero(i));
        }

        assertEquals(
                0,
                set.size(),
                "Все оставшиеся позиции удалены, иначе счётчик размера расходится с содержимым. "
                        + "При смене remove проверь опустошение переполненного чанка");
        assertTrue(
                PlacedBlockCodec.encode(set).isEmpty(),
                "Пустой чанк забывается вместе с пометкой переполнения, иначе в файле остаётся пустая запись. "
                        + "При смене remove проверь флаг после удаления последней позиции");
    }

    @Test
    void pack_OutOfRange_ThrowsWithCoordinate() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> PlacedBlockSet.pack(new BlockCoord(0, 2048, 0)),
                "y = 2048 не умещается в 12 бит, иначе координата молча меняет знак и блок попадает в другое "
                        + "место. При смене упаковки проверь границу 2047 и 2048");

        assertTrue(
                error.getMessage().contains("y=2048"),
                "Ошибка называет координату, иначе не понять, какое значение исправлять. Фактическое: "
                        + error.getMessage() + ". При смене текста проверь «y=2048»");
    }

    @Test
    void pack_Limits_RoundTripExactly() {
        BlockCoord low = new BlockCoord(-33_554_432, -2048, -33_554_432);
        BlockCoord high = new BlockCoord(33_554_431, 2047, 33_554_431);

        assertEquals(
                low,
                PlacedBlockSet.unpack(PlacedBlockSet.pack(low)),
                "Нижняя граница сжатия восстанавливается точно, иначе крайние координаты искажаются. "
                        + "При смене упаковки проверь X/Z -33554432 и Y -2048");
        assertEquals(
                high,
                PlacedBlockSet.unpack(PlacedBlockSet.pack(high)),
                "Верхняя граница сжатия восстанавливается точно, иначе крайние координаты искажаются. "
                        + "При смене упаковки проверь X/Z 33554431 и Y 2047");
    }
}
