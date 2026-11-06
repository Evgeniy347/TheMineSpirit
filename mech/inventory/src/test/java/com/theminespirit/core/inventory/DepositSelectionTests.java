package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class DepositSelectionTests {
    private static final ValueTable VALUES = ValueTable.load();
    private static final BaseKit KIT = KitFor.kitFor("mining", false);

    @Test
    void surplus_KitOnlyInventory_GivesNothing() {
        List<Stack> inventory = List.of(
                new Stack("minecraft:iron_sword", 0, 1, 1),
                new Stack("minecraft:bow", 0, 1, 1),
                new Stack("minecraft:arrow", 0, 16, 64),
                new Stack("minecraft:bread", 0, 16, 64),
                new Stack("minecraft:iron_pickaxe", 0, 1, 1),
                new Stack("minecraft:torch", 0, 32, 64));

        assertEquals(
                List.of(),
                DepositSelection.surplus(inventory, KIT, VALUES, -1),
                "Инвентарь ровно по набору не даёт ничего к сдаче: иначе дух сдал бы на склад свой меч, "
                        + "кирку, еду и факелы и остался без снаряжения");
    }

    @Test
    void surplus_ItemOutsideKit_IsGivenAwayWhole() {
        List<Stack> inventory = List.of(new Stack("minecraft:cobblestone", 0, 64, 64));

        assertEquals(
                List.of(new Slice(0, 64)),
                DepositSelection.surplus(inventory, KIT, VALUES, -1),
                "Булыжник вне набора сдаётся целиком: иначе шахтёр таскал бы добычу вместо места под руду");
    }

    @Test
    void surplus_CurrentToolOutsideKit_Stays() {
        List<Stack> inventory = List.of(
                new Stack("minecraft:iron_shovel", 0, 1, 1),
                new Stack("minecraft:cobblestone", 0, 64, 64),
                new Stack("minecraft:iron_pickaxe", 0, 1, 1),
                new Stack("minecraft:bread", 0, 20, 64));

        assertEquals(
                List.of(new Slice(1, 64), new Slice(3, 4)),
                DepositSelection.surplus(inventory, KIT, VALUES, 0),
                "Текущий инструмент в слоте 0 не входит в набор шахты (лопата), но сдавать его нельзя: "
                        + "без него дух не продолжит работу. Хлеб сверх квоты 16 (4 штуки) и булыжник уходят");
    }

    @Test
    void surplus_CurrentToolOutsideKeepQuota_Stays() {
        List<Stack> inventory = List.of(
                new Stack("minecraft:wooden_pickaxe", 0, 1, 1),
                new Stack("minecraft:iron_pickaxe", 0, 1, 1),
                new Stack("minecraft:diamond_pickaxe", 0, 1, 1));

        assertEquals(
                List.of(),
                DepositSelection.surplus(inventory, KIT, VALUES, 0),
                "Набор шахты держит две лучшие кирки, деревянная попала бы в излишек, но она текущий инструмент: "
                        + "сдать её значит остановить работу; две лучшие остаются, текущая тоже");
    }

    @Test
    void surplus_NeverGiveAwayItem_Stays() {
        List<Stack> inventory = List.of(new Stack("minecraft:shulker_box", 0, 1, 1));

        assertEquals(
                List.of(),
                DepositSelection.surplus(inventory, KIT, VALUES, -1),
                "Шалкер-бокс в never_give_away базового набора не сдаётся никогда: иначе вместе с ним "
                        + "на склад уехало бы всё, что в нём лежит");
    }

    @Test
    void surplus_EmptyInventory_GivesNothing() {
        assertEquals(
                List.of(),
                DepositSelection.surplus(List.of(), KIT, VALUES, -1),
                "Пустой инвентарь сдавать нечего: вызов обязан вернуть пустой список, а не упасть");
    }

    @Test
    void surplus_EmptySlots_AreIgnored() {
        List<Stack> inventory = Arrays.asList(null, new Stack("minecraft:cobblestone", 0, 12, 64), null);

        assertEquals(
                List.of(new Slice(1, 12)),
                DepositSelection.surplus(inventory, KIT, VALUES, -1),
                "Пустые слоты не дают излишка и не сбивают нумерацию: сдаётся только то, что реально лежит");
    }
}
