package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WarehouseSpaceTests {
    private static final BlockCoord ORIGIN = new BlockCoord(0, 64, 0);
    private static final int CHEST_SLOTS = 27;

    @Test
    void minFreePercent_ProductionValue_IsTwenty() {
        assertEquals(
                20,
                WarehouseSpace.MIN_FREE_PERCENT,
                "Склад обязан сохранять минимум 20% свободного места (решение владельца 14.10): когда запас "
                        + "меньше, склад уже полон и его пора расширять, а не забивать остатком");
    }

    @Test
    void canTakeNewSlot_SevenFreeOfTwentySeven_KeepsReserve() {
        ContainerSnapshot chest = chestWithFreeSlots(7);

        assertTrue(
                WarehouseSpace.canTakeNewSlot(chest, WarehouseSpace.MIN_FREE_PERCENT),
                "Из 27 слотов свободны 7: после одной новой стопки останется 6 (22%), минимум 20% сохранён — "
                        + "склад ещё принимает сдачу");
    }

    @Test
    void canTakeNewSlot_SixFreeOfTwentySeven_BreaksReserve() {
        ContainerSnapshot chest = chestWithFreeSlots(6);

        assertFalse(
                WarehouseSpace.canTakeNewSlot(chest, WarehouseSpace.MIN_FREE_PERCENT),
                "Из 27 слотов свободны 6: после новой стопки останется 5 (18%), ниже минимума 20% — "
                        + "склад считается полным, дух не забивает его под завязку");
    }

    @Test
    void canTakeNewSlot_FullChest_Rejects() {
        ContainerSnapshot chest = chestWithFreeSlots(0);

        assertFalse(
                WarehouseSpace.canTakeNewSlot(chest, WarehouseSpace.MIN_FREE_PERCENT),
                "В полный сундук новую стопку не положить: отказ обязан быть и при нулевом запасе свободы");
    }

    @Test
    void canTakeNewSlot_ZeroReserve_MatchesFreeSlotRule() {
        ContainerSnapshot chest = chestWithFreeSlots(1);

        assertTrue(
                WarehouseSpace.canTakeNewSlot(chest, 0),
                "Без требования запаса (обычная разгрузка) достаточно одного свободного слота: иначе "
                        + "старое поведение unload изменилось бы молча");
    }

    @Test
    void needsChest_NinetyFivePercentTaken_NewChestNeeded() {
        assertTrue(
                WarehouseSpace.needsChest(5, 100, WarehouseSpace.MIN_FREE_PERCENT),
                "Склад занят на 95%: свободно меньше 20% — доска ставит задание «поставить сундук» "
                        + "(решение владельца 25.10)");
    }

    @Test
    void needsChest_SeventyPercentTaken_NoNewChest() {
        assertFalse(
                WarehouseSpace.needsChest(30, 100, WarehouseSpace.MIN_FREE_PERCENT),
                "Склад занят на 70%: запаса 20% хватает — лишний сундук съедает дерево этапа 1");
        assertFalse(
                WarehouseSpace.needsChest(0, 0, WarehouseSpace.MIN_FREE_PERCENT),
                "Сундуков ещё нет: первый ставит шаг этапа 1, правило места не подменяет его");
    }

    private static ContainerSnapshot chestWithFreeSlots(int free) {
        List<Stack> slots = new ArrayList<>();
        for (int slot = 0; slot < CHEST_SLOTS - free; slot++) {
            slots.add(new Stack("minecraft:dirt", 0, 64, 64));
        }
        for (int slot = 0; slot < free; slot++) {
            slots.add(null);
        }
        return new ContainerSnapshot(ORIGIN, slots);
    }
}
