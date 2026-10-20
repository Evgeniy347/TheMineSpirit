package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BuildClutterTests {
    private static final Set<String> NEEDED = Set.of("minecraft:oak_planks", "minecraft:cobblestone");

    private static List<String> slots(String... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    @Test
    void junk_MobDropsBesideMaterials_FreesOnlyDrops() {
        List<String> inventory = slots(
                "minecraft:oak_planks", "minecraft:rotten_flesh", "minecraft:bone", null, "minecraft:cobblestone");
        assertEquals(
                List.of(1, 2),
                BuildClutter.junk(inventory, NEEDED, Set.of()),
                "дроп мобов (гнилая плоть, кость) стройке не нужен — его слоты освобождаются, материалы остаются");
    }

    @Test
    void junk_KeptSlot_StaysEvenIfNotNeeded() {
        List<String> inventory = slots("minecraft:iron_pickaxe", "minecraft:rotten_flesh");
        assertEquals(
                List.of(1),
                BuildClutter.junk(inventory, NEEDED, Set.of(0)),
                "набор духа (кирка) в обычной игре не отдаётся, даже если стройке не нужен");
    }

    @Test
    void toRelease_FullInventoryOfJunk_FreesWhatThePortionNeeds() {
        List<String> inventory = new ArrayList<>(Collections.nCopies(36, "minecraft:rotten_flesh"));
        List<Integer> junk = BuildClutter.junk(inventory, NEEDED, Set.of());
        assertEquals(
                10,
                BuildClutter.toRelease(junk, 0, 10).size(),
                "инвентарь, забитый дропом, освобождает под порцию в 10 стопок 10 слотов: заход не падает");
    }

    @Test
    void toRelease_PortionLargerThanJunk_FreesAllJunk() {
        List<Integer> junk = List.of(3, 5);
        assertEquals(
                List.of(3, 5),
                BuildClutter.toRelease(junk, 0, 30),
                "лишнего меньше, чем не хватает слотов, — освобождается всё лишнее, материалы не трогаются");
    }

    @Test
    void toRelease_SomeFreeSlots_KeepsJunk() {
        assertEquals(
                List.of(),
                BuildClutter.toRelease(List.of(0, 1), 2, 10),
                "есть свободные слоты — заход идёт порцией в них (заходы с двумя свободными слотами, решение 20.10), "
                        + "лишнее не выбрасывается и не носится на склад зря");
    }
}
