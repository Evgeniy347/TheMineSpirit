package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class DepositSelectionProperties {
    private static final ValueTable VALUES = ValueTable.load();
    private static final BaseKit KIT = KitFor.kitFor("mining", false);

    @Provide
    Arbitrary<List<Stack>> inventories() {
        Arbitrary<Stack> stack = Combinators.combine(
                        Arbitraries.of(
                                "wooden_pickaxe",
                                "iron_pickaxe",
                                "diamond_pickaxe",
                                "iron_shovel",
                                "stone_axe",
                                "iron_sword",
                                "bread",
                                "cooked_beef",
                                "apple",
                                "torch",
                                "cobblestone",
                                "dirt",
                                "oak_log",
                                "iron_ingot",
                                "crafting_table",
                                "bucket"),
                        Arbitraries.integers().between(0, 3),
                        Arbitraries.integers().between(1, 64))
                .as((item, variant, count) -> new Stack(item, variant, count, 64));
        return stack.list().ofMaxSize(12);
    }

    @Property
    void surplus_WithoutProtectedSlot_KitIsNeverGivenAway(@ForAll("inventories") List<Stack> inventory) {
        List<Stack> left = remove(inventory, DepositSelection.surplus(inventory, KIT, VALUES, -1));

        assertTrue(
                KIT.isSatisfiedBy(left, VALUES),
                "Без защищённого инструмента после сдачи инвентарь обязан удовлетворять набору: набор "
                        + "(инструменты, еда, броня) не сдаётся никогда. Было " + inventory + ", осталось " + left);
    }

    @Property
    void surplus_ProtectedSlot_OnlyProtectsThatSlot(@ForAll("inventories") List<Stack> inventory) {
        int protectedSlot = firstOccupied(inventory);
        List<Slice> unprotected = DepositSelection.surplus(inventory, KIT, VALUES, -1);
        List<Slice> protectedSlices = DepositSelection.surplus(inventory, KIT, VALUES, protectedSlot);
        List<Slice> expected = new ArrayList<>();
        for (Slice slice : unprotected) {
            if (slice.slot() != protectedSlot) {
                expected.add(slice);
            }
        }

        assertEquals(
                expected,
                protectedSlices,
                "Защита текущего инструмента обязана убирать из сдачи только его слот, не меняя решение "
                        + "по остальным: иначе дух сдал бы часть набора или оставил лишнее. Инвентарь "
                        + inventory + ", слот " + protectedSlot);
    }

    @Property
    void surplus_GivenTwiceWithoutProtection_GivesNothingMore(@ForAll("inventories") List<Stack> inventory) {
        List<Stack> left = remove(inventory, DepositSelection.surplus(inventory, KIT, VALUES, -1));

        assertEquals(
                List.of(),
                DepositSelection.surplus(left, KIT, VALUES, -1),
                "Повторная сдача того же инвентаря без защиты обязана быть пустой: иначе два вызова подряд "
                        + "сдали бы разное количество. Инвентарь " + inventory + ", осталось " + left);
    }

    private static int firstOccupied(List<Stack> inventory) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.get(slot) != null) {
                return slot;
            }
        }
        return -1;
    }

    private static List<Stack> remove(List<Stack> inventory, List<Slice> slices) {
        List<Stack> left = new ArrayList<>(inventory);
        for (Slice slice : slices) {
            Stack stack = left.get(slice.slot());
            int kept = stack.count() - slice.count();
            left.set(slice.slot(), kept == 0 ? null : stack.withCount(kept));
        }
        return left;
    }
}
