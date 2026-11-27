package com.theminespirit.core.board.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class TownAuditCacheTests {

    @Test
    void blockDict_RepeatedId_ReturnsSameCodeAndKeepsZeroUnknown() {
        BlockDict dict = new BlockDict();

        short first = dict.intern("minecraft:dirt");

        assertEquals(
                first,
                dict.intern("minecraft:dirt"),
                "Один id обязан получать один код: разные коды раздувают кэш чанка, иначе сравнение клетки с "
                        + "ожиданием читает чужой блок. При смене словаря перепроверь BlockDict.MAX_CODES и "
                        + "ChunkCache.put.");
        assertTrue(
                first != BlockDict.UNKNOWN,
                "Код 0 зарезервирован под неизвестную клетку: если его займёт первый блок, пустой кэш начнёт "
                        + "читаться как заполненный, иначе аудит объявит посёлку ложную готовность. "
                        + "При смене словаря перепроверь ChunkCache.known и счётчик unknown.");
        assertFalse(
                dict.known(BlockDict.UNKNOWN),
                "Неизвестный код обязан оставаться неизвестным: иначе «не знаю» не отличить от «air», и промах "
                        + "кэша станет нормой вместо инцидента. При смене словаря перепроверь ChunkCache.block "
                        + "и классификацию NOT_FILLED.");
    }

    @Test
    void chunkCache_NegativeChunkCoordinates_ReadBackOwnCell() {
        ChunkCache cache = new ChunkCache(new ChunkCell(-16, 33), new BlockDict(), 60, 8);
        BlockCoord own = new BlockCoord(-256, 63, 528);

        cache.put(own, "minecraft:dirt");

        assertEquals(
                "minecraft:dirt",
                cache.block(own),
                "Отрицательные координаты обязаны попадать в свой чанк: сложение вместо побитовой маски сдвигает "
                        + "клетку на соседний столб, иначе аудит путает два участка и лечит не там. "
                        + "При смене индекса перепроверь ChunkCache.holds на координатах меньше нуля.");
        assertNull(
                cache.block(new BlockCoord(-255, 63, 528)),
                "Соседний столб остаётся неизвестным: если запись задела его, индекс считается неверно, иначе "
                        + "группа соберётся из чужих блоков. При смене индекса перепроверь ChunkCache.index.");
    }

    @Test
    void chunkCache_CellOutsideWindow_RejectsWriteAndRepeatCountsOnce() {
        ChunkCache cache = new ChunkCache(new ChunkCell(0, 0), new BlockDict(), 60, 2);

        assertTrue(
                cache.empty(),
                "Свежий кэш чанка пуст: если он считается заполненным, аудит объявит неизвестные клетки "
                        + "чистыми, иначе ни одна группа не будет выдана. При смене наполнения перепроверь "
                        + "адаптер ChunkEvent.Load (block 4).");
        assertThrows(
                IllegalArgumentException.class,
                () -> cache.put(new BlockCoord(0, 100, 0), "minecraft:air"),
                "Запись вне окна кэша обязана падать: иначе клетки выше envelope затирают соседей, и группа "
                        + "улицы соберётся из блоков другой высоты. При смене окна перепроверь границы envelope "
                        + "в ExpectIndex (block 3).");

        cache.put(new BlockCoord(3, 60, 4), "minecraft:stone");
        int filled = cache.filled();
        cache.put(new BlockCoord(3, 60, 4), "minecraft:stone");

        assertEquals(
                filled,
                cache.filled(),
                "Повторная запись той же клетки не увеличивает заполненность: иначе filled() перевесит ёмкость, "
                        + "unknown уйдёт в минус, и приёмка «кэш залит» станет ложной. При смене счётчика "
                        + "перепроверь ChunkCache.set.");
    }

    @Test
    void changeRing_Overflow_KeepsNewestAndFindsCell() {
        ChangeRing ring = new ChangeRing(2);
        BlockCoord early = new BlockCoord(1, 64, 1);
        BlockCoord late = new BlockCoord(2, 64, 2);

        ring.add(new ChangeRing.Change(
                10, early, "minecraft:air", "minecraft:dirt", ChangeRing.Actor.PLAYER, "t", "", ""));
        ring.add(new ChangeRing.Change(
                11,
                late,
                "minecraft:dirt",
                "minecraft:air",
                ChangeRing.Actor.SPIRIT,
                "build",
                "7",
                "group:ground@0,0"));

        List<ChangeRing.Change> all = ring.all();

        assertEquals(
                2,
                all.size(),
                "Кольцо хранит ровно свою ёмкость: выход за границу массива теряет изменения чанка, иначе "
                        + "инцидент промаха кэша не воспроизвести. При смене ёмкости перепроверь "
                        + "ChangeRing.CAPACITY и формат сообщения AUDIT-MISS.");
        assertEquals(
                late,
                all.get(all.size() - 1).cell(),
                "Последняя запись — самая свежая: инцидент печатает именно её; старая улика объяснит не тот "
                        + "промах, иначе разбор уйдёт в сторону. При смене порядка перепроверь ChangeRing.last "
                        + "и AuditMiss.text.");

        ring.add(new ChangeRing.Change(
                12, early, "minecraft:air", "minecraft:stone", ChangeRing.Actor.NATURAL, "", "", ""));

        assertTrue(
                ring.seenSince(early, 12),
                "Свежее изменение клетки видно по тику: каскад «свой билет не грязним» решает по этому факту, "
                        + "иначе ошибка пометит чужую руку своей. При смене журнала перепроверь обработку "
                        + "BlockChangeWatch (block 5).");
        assertFalse(
                ring.seenSince(late, 12),
                "Старое изменение не считается свежим: иначе правка прошлого тика оправдает новый промах, а "
                        + "группа останется в работе с неверным снимком. При смене журнала перепроверь "
                        + "ChangeRing.lastAt.");
    }
}
