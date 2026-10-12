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

        assertTrue(added, "Новая позиция добавляется");
        assertTrue(set.contains(new BlockCoord(5, 64, -3)), "Добавленная позиция найдена: иначе бревно игрока срубят");
        assertEquals(1, set.size(), "Одна позиция в наборе");
    }

    @Test
    void add_SamePositionTwice_IsIdempotent() {
        PlacedBlockSet set = new PlacedBlockSet();
        set.add(new BlockCoord(5, 64, -3));

        boolean again = set.add(new BlockCoord(5, 64, -3));

        assertFalse(again, "Повторная отметка той же позиции не новая");
        assertEquals(1, set.size(), "Повтор не растит набор: иначе событие установки блока дважды съело бы лимит чанка");
    }

    @Test
    void remove_AddedPosition_IsGone() {
        PlacedBlockSet set = new PlacedBlockSet();
        set.add(new BlockCoord(5, 64, -3));

        boolean removed = set.remove(new BlockCoord(5, 64, -3));

        assertTrue(removed, "Добавленная позиция удаляется");
        assertFalse(set.contains(new BlockCoord(5, 64, -3)), "Удалённая позиция не найдена: блок сломан, отметка не должна держать чанк");
        assertEquals(0, set.size(), "Набор пуст");
    }

    @Test
    void remove_AbsentPosition_ReturnsFalse() {
        PlacedBlockSet set = new PlacedBlockSet();

        assertFalse(set.remove(new BlockCoord(1, 1, 1)), "Удаление отсутствующей позиции — не ошибка, а false: ломают и природные блоки");
    }

    @Test
    void contains_NegativeCoordinates_AreDistinctFromPositive() {
        PlacedBlockSet set = new PlacedBlockSet();
        set.add(new BlockCoord(-1, -60, -17));

        assertTrue(set.contains(new BlockCoord(-1, -60, -17)), "Отрицательные координаты сохраняются точно");
        assertFalse(set.contains(new BlockCoord(1, 60, 17)), "Зеркальные положительные координаты не совпадают с отрицательными");
        assertFalse(set.contains(new BlockCoord(15, -60, -17)), "Соседний чанк не совпадает");
    }

    @Test
    void add_MoreThanLimitInOneChunk_EvictsTheOldestAndMarksOverflow() {
        PlacedBlockSet set = new PlacedBlockSet();
        for (int i = 0; i < 4097; i++) {
            set.add(inChunkZero(i));
        }

        assertEquals(4096, set.size(), "В чанке держится не больше 4096 позиций");
        assertFalse(set.contains(inChunkZero(0)), "Самая старая позиция вытеснена");
        assertTrue(set.contains(inChunkZero(1)), "Вторая по возрасту осталась");
        assertTrue(set.contains(inChunkZero(4096)), "Новейшая позиция сохранена");
        assertTrue(set.isOverflowed(inChunkZero(0)), "Вытеснение помечает чанк: для него действует эвристика «здание», а не только набор");
        assertFalse(set.isOverflowed(new BlockCoord(16, 0, 0)), "Соседний чанк без вытеснений не помечен");
    }

    @Test
    void add_ExactlyLimitInOneChunk_DoesNotEvict() {
        PlacedBlockSet set = new PlacedBlockSet();
        for (int i = 0; i < 4096; i++) {
            set.add(inChunkZero(i));
        }

        assertEquals(4096, set.size(), "Ровно 4096 позиций помещаются целиком");
        assertTrue(set.contains(inChunkZero(0)), "Самая старая позиция при ровно 4096 ещё на месте");
        assertFalse(set.isOverflowed(inChunkZero(0)), "Без вытеснения чанк не помечен");
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

        assertEquals(0, set.size(), "Все оставшиеся позиции удалены");
        assertFalse(set.isOverflowed(inChunkZero(0)), "Пустой чанк забывается вместе с пометкой переполнения");
    }

    @Test
    void pack_OutOfRange_ThrowsWithCoordinate() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> PlacedBlockSet.pack(new BlockCoord(0, 2048, 0)),
                "y = 2048 не умещается в 12 бит");

        assertTrue(error.getMessage().contains("y=2048"), "Ошибка называет координату. Фактическое: " + error.getMessage());
    }

    @Test
    void pack_Limits_RoundTripExactly() {
        BlockCoord low = new BlockCoord(-33_554_432, -2048, -33_554_432);
        BlockCoord high = new BlockCoord(33_554_431, 2047, 33_554_431);

        assertEquals(low, PlacedBlockSet.unpack(PlacedBlockSet.pack(low)), "Нижняя граница сжатия восстанавливается точно");
        assertEquals(high, PlacedBlockSet.unpack(PlacedBlockSet.pack(high)), "Верхняя граница сжатия восстанавливается точно");
    }
}
