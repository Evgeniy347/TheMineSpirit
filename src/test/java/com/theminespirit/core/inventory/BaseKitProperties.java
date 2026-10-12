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

class BaseKitProperties {
    private static final ValueTable VALUES = ValueTable.load();
    private static final BaseKit KIT = BaseKit.load();

    @Provide
    Arbitrary<List<Stack>> inventories() {
        Arbitrary<Stack> stack = Combinators.combine(
                Arbitraries.of("wooden_pickaxe", "iron_pickaxe", "diamond_pickaxe", "stone_axe", "iron_sword",
                        "bread", "cooked_beef", "apple", "torch", "cobblestone", "dirt", "oak_log", "iron_ingot",
                        "crafting_table", "bucket"),
                Arbitraries.integers().between(0, 3),
                Arbitraries.integers().between(1, 64))
                .as((item, variant, count) -> new Stack(item, variant, count, 64));
        return stack.list().ofMaxSize(12);
    }

    @Property
    void surplus_InventoryAfterGivingAway_SatisfiesKit(@ForAll("inventories") List<Stack> inventory) {
        List<Slice> surplus = KIT.surplus(inventory, VALUES);
        List<Stack> left = remove(inventory, surplus);

        assertTrue(KIT.isSatisfiedBy(left, VALUES),
                "Инвентарь без излишка обязан удовлетворять базовому набору: разгрузка за один проход "
                        + "должна оставить духу ровно набор, а не часть его. Было " + inventory + ", излишек "
                        + surplus + ", осталось " + left);
    }

    @Property
    void surplus_GivenAwayTwice_GivesNothingMore(@ForAll("inventories") List<Stack> inventory) {
        List<Stack> left = remove(inventory, KIT.surplus(inventory, VALUES));

        assertEquals(List.of(), KIT.surplus(left, VALUES),
                "Повторный расчёт излишка обязан быть пустым: иначе разгрузка одного инвентаря двумя "
                        + "проходами отдала бы в контейнер разное количество, и скрипт unload зависел бы "
                        + "от числа вызовов. Инвентарь " + inventory + ", осталось " + left);
    }

    @Property
    void surplus_SliceCount_NeverExceedsSlotCount(@ForAll("inventories") List<Stack> inventory) {
        for (Slice slice : KIT.surplus(inventory, VALUES)) {
            Stack stack = inventory.get(slice.slot());
            assertTrue(stack != null && slice.count() <= stack.count(),
                    "Излишек слота " + slice.slot() + " (" + slice.count() + ") не может превышать то, "
                            + "что в слоте есть (" + stack + "): иначе перенос отдал бы предметы, "
                            + "которых у духа нет. Инвентарь " + inventory);
        }
    }

    @Property
    void surplus_TotalCountPerItem_PreservedAfterGivingAway(@ForAll("inventories") List<Stack> inventory) {
        List<Stack> left = remove(inventory, KIT.surplus(inventory, VALUES));

        for (String item : items(inventory)) {
            int before = countOf(inventory, item);
            int after = countOf(left, item);
            assertTrue(after <= before,
                    "После вычитания излишка предметов '" + item + "' не может стать больше: было " + before
                            + ", стало " + after + ". Инвентарь " + inventory + ", осталось " + left);
        }
    }

    private static List<String> items(List<Stack> inventory) {
        List<String> items = new ArrayList<>();
        for (Stack stack : inventory) {
            if (stack != null && !items.contains(stack.item())) {
                items.add(stack.item());
            }
        }
        return items;
    }

    private static int countOf(List<Stack> inventory, String item) {
        int total = 0;
        for (Stack stack : inventory) {
            if (stack != null && stack.item().equals(item)) {
                total += stack.count();
            }
        }
        return total;
    }

    private static List<Stack> remove(List<Stack> inventory, List<Slice> surplus) {
        List<Stack> left = new ArrayList<>(inventory);
        for (Slice slice : surplus) {
            Stack stack = left.get(slice.slot());
            int kept = stack.count() - slice.count();
            left.set(slice.slot(), kept == 0 ? null : stack.withCount(kept));
        }
        return left;
    }
}
