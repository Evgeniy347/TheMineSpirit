package com.theminespirit.neoforge.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ContainerAccessTests {
    @Test
    void allowed_ItemStorageBlocks_AreAccepted() {

        assertTrue(
                ContainerAccess.allowedId("minecraft:hopper"),
                "Воронка принимается как контейнер: иначе её содержимое нельзя перенести до сноса. При смене "
                        + "allowed_ItemStorageBlocks_AreAccepted проверь ChestDemolition и ContainerFinder.");
        assertTrue(
                ContainerAccess.allowedId("minecraft:furnace"),
                "Печь принимается как контейнер: иначе ресурсы внутри пропадут при сносе. При смене "
                        + "allowed_ItemStorageBlocks_AreAccepted проверь FloatingCleanup и ChestPlans.");
        assertTrue(
                ContainerAccess.allowedId("minecraft:dispenser"),
                "Раздатчик хранит предметы и подчиняется переносу до сноса: иначе содержимое выпадет без "
                        + "склада. При смене allowed_ItemStorageBlocks_AreAccepted проверь ContainerAccess.read.");
        assertTrue(
                ContainerAccess.allowedId("minecraft:barrel"),
                "Бочка остаётся допустимым складским контейнером: иначе существующая логистика сломается. "
                        + "При смене allowed_ItemStorageBlocks_AreAccepted проверь StockReader и StorePost.");
    }

    @Test
    void allowed_EnderChest_IsRejected() {
        assertFalse(
                ContainerAccess.allowedId("minecraft:ender_chest"),
                "Эндер-сундук не имеет доступного общего инвентаря: иначе дух попытается вынести чужие "
                        + "предметы. При смене allowed_EnderChest_IsRejected проверь ContainerAccess.read.");
    }
}
