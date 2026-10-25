package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InventorySpaceTests {
    @Test
    void holds_EmptyInventory_AcceptsOneStack() {
        List<Stack> inventory = List.of();
        Stack incoming = new Stack("cobblestone", 0, 10, 64);

        assertTrue(
                InventorySpace.holds(inventory, incoming),
                "Пустой инвентарь обязан вмещать стопку: правило «не выкидывать» начинается с проверки места, "
                        + "и пустой инвентарь место имеет");
    }

    @Test
    void holds_FullInventory_RejectsAdditionalStack() {

        List<Stack> full = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            full.add(new Stack("cobblestone", 0, 64, 64));
        }
        Stack incoming = new Stack("cobblestone", 0, 1, 64);

        assertFalse(
                InventorySpace.holds(full, incoming),
                "Полный инвентарь не обязан вмещать ещё один предмет: иначе дух выбросил бы стопку, "
                        + "нарушая политику «не выкидывать предметы»");
    }

    @Test
    void holds_PartialStack_TopsUpWithoutNewSlot() {
        List<Stack> inventory = new ArrayList<>();
        inventory.add(new Stack("cobblestone", 0, 63, 64));
        for (int i = 1; i < 36; i++) {
            inventory.add(new Stack("dirt", 0, 64, 64));
        }
        Stack incoming = new Stack("cobblestone", 0, 1, 64);

        assertTrue(
                InventorySpace.holds(inventory, incoming),
                "Частичная стопка булыжника обязана принять один предмет в тот же слот: место есть, "
                        + "иначе отказ был бы ложным");
    }

    @Test
    void holds_ListIncoming_AllMustFit() {
        List<Stack> inventory = new ArrayList<>();
        for (int i = 0; i < 35; i++) {
            inventory.add(new Stack("cobblestone", 0, 64, 64));
        }
        inventory.add(new Stack("cobblestone", 0, 63, 64));
        List<Stack> incoming = List.of(new Stack("cobblestone", 0, 1, 64), new Stack("cobblestone", 0, 1, 64));

        assertFalse(
                InventorySpace.holds(inventory, incoming),
                "Двух предметов при одном свободном месте не влезает: проверка обязана учитывать весь набор, "
                        + "а не только первый");
    }
}
