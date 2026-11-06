package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class FarmSeedReserveTests {
    private static final ValueTable VALUES = ValueTable.load();
    private static final BaseKit FARM_KIT = KitFor.kitFor("farming", false);

    @Test
    void surplus_HarvestWithSeeds_GivesProduceAndKeepsSeedReserve() {
        List<Stack> inventory = List.of(
                new Stack("minecraft:wheat", 0, 12, 64),
                new Stack("minecraft:wheat_seeds", 0, 24, 64),
                new Stack("minecraft:iron_hoe", 0, 1, 1));

        List<Slice> surplus = DepositSelection.surplus(inventory, FARM_KIT, VALUES, -1);

        assertEquals(
                List.of(new Slice(0, 12), new Slice(1, 8)),
                surplus,
                "Урожай сдаётся на склад целиком, а из 24 семян уходит только излишек (8): запас на посадку "
                        + "(16) и мотыга набора остаются у фермы. Иначе сеять следующую грядку нечем");
    }

    @Test
    void surplus_SeedsBelowReserve_GivesNoSeeds() {
        List<Stack> inventory = List.of(new Stack("minecraft:wheat_seeds", 0, 9, 64));

        assertEquals(
                List.of(),
                DepositSelection.surplus(inventory, FARM_KIT, VALUES, -1),
                "Семян меньше запаса на посадку (16) — на склад не уходит ни одно: иначе грядка останется "
                        + "незасеянной ради чужого сундука");
    }
}
