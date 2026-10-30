package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class WarehouseSpotTests {
    @Test
    void line_StorageSlot_NamesSiteSlotAndReason() {
        String line =
                WarehouseSpot.line(1, new BlockCoord(10, 64, -5), new BlockCoord(10, 64, -5), "слот storage плана");

        assertTrue(line.contains("склад уровня 1"), "Строка склада называет уровень: по нему ищут warehouse_1");
        assertTrue(
                line.contains("site=10,64,-5"), "Строка склада называет место постройки: без него склад ищут глазами");
        assertTrue(
                line.contains("slot=10,64,-5"),
                "Строка склада называет выбранный слот storage: так видно источник места");
        assertTrue(
                line.contains("причина=слот storage плана"),
                "Строка склада называет причину выбора: без неё не понять, почему место именно такое");
    }

    @Test
    void line_FallbackHome_NamesHomeAndReason() {
        String line = WarehouseSpot.line(
                1, new BlockCoord(3, 70, 8), null, "в плане поселения нет слота storage: склад у дома духов");

        assertTrue(line.contains("site=3,70,8"), "Запасное место у дома тоже пишется: иначе выбор дома не виден");
        assertTrue(line.contains("slot=—"), "Без слота storage поле пишется прочерком, а не пропускается");
        assertTrue(
                line.contains("причина=в плане поселения нет слота storage"),
                "Строка называет причину запасного места: по ней чинят план поселения, а не гадают");
    }
}
