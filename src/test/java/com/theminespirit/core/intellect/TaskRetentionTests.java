package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.inventory.InventoryFill;
import org.junit.jupiter.api.Test;

class TaskRetentionTests {
    private static final InventoryNormTable TABLE = InventoryNormTable.load();

    @Test
    void holds_MineWithOneFreeSlot_KeepsTask() {
        assertTrue(
                TaskRetention.holds(ActivityId.MINE, 1, TABLE),
                "Шахтёр держит задачу до последней свободной ячейки: иначе он бросает жилу, не добрав руду");
    }

    @Test
    void holds_MineWithoutFreeSlot_BreaksTask() {
        assertFalse(
                TaskRetention.holds(ActivityId.MINE, 0, TABLE),
                "Без свободной ячейки шахтёр бросает задачу: дальше добыча некуда ляжет");
    }

    @Test
    void holds_ChopWithOneFreeSlot_KeepsTask() {
        assertTrue(
                TaskRetention.holds(ActivityId.CHOP, 1, TABLE),
                "Рубка удерживается так же, как шахта: до последней свободной ячейки");
    }

    @Test
    void holds_FarmWithTwoFreeSlots_BreaksTask() {
        assertFalse(
                TaskRetention.holds(ActivityId.FARM, 2, TABLE),
                "Ферма бросает задачу раньше шахтёра (порог 2): урожай успевает дойти до базы");
    }

    @Test
    void holds_FarmWithThreeFreeSlots_KeepsTask() {
        assertTrue(
                TaskRetention.holds(ActivityId.FARM, 3, TABLE),
                "Пока у фермы больше двух свободных ячеек, она продолжает работу");
    }

    @Test
    void holds_FillWithEnoughFreeSlots_UsesFreeSlots() {
        InventoryFill fill = new InventoryFill(33, 36, 0, 33);

        assertTrue(
                TaskRetention.holds(ActivityId.MINE, fill, TABLE),
                "Удержание по заполнению берёт свободные ячейки из заполнения: 3 свободных — шахтёр работает");
    }

    @Test
    void holds_NegativeFreeSlots_Throws() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> TaskRetention.holds(ActivityId.MINE, -1, TABLE),
                "Отрицательное число свободных ячеек недопустимо: это не заполнение инвентаря");
        assertTrue(error.getMessage().contains("-1"), "Сообщение называет число: при падении видно вход");
    }
}
