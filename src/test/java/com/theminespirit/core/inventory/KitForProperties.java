package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class KitForProperties {
    private static final ValueTable VALUES = ValueTable.load();
    private static final KitFor KIT_FOR = KitFor.load();

    @Provide
    Arbitrary<String> activities() {
        return Arbitraries.of("mining", "woodcutting", "farming", "building", "unknown");
    }

    @Property
    void adult_AnyActivity_ConstantPartFullyKept(@ForAll("activities") String activity) {
        List<Stack> inventory = List.of(
                new Stack("iron_sword", 0, 1, 1),
                new Stack("bow", 0, 1, 1),
                new Stack("arrow", 0, 16, 64),
                new Stack("bread", 0, 16, 64),
                new Stack("torch", 0, 24, 64));

        List<Slice> surplus = KIT_FOR.kit(activity, false).surplus(inventory, VALUES);

        assertEquals(
                List.of(),
                surplus,
                "постоянная часть всегда входит у взрослого: деятельность '" + activity
                        + "' добавляет свой набор сверх, но не имеет права вытеснить меч, лук, стрелы, "
                        + "еду или факелы из постоянной части");
    }

    @Property
    void child_AnyActivity_OnlyFoodKept(@ForAll("activities") String activity) {
        List<Stack> inventory = List.of(
                new Stack("iron_sword", 0, 1, 1),
                new Stack("bow", 0, 1, 1),
                new Stack("arrow", 0, 16, 64),
                new Stack("bread", 0, 16, 64),
                new Stack("torch", 0, 24, 64),
                new Stack("iron_pickaxe", 0, 1, 1),
                new Stack("iron_axe", 0, 1, 1),
                new Stack("wheat_seeds", 0, 16, 64),
                new Stack("cobblestone", 0, 10, 64),
                new Stack("oak_planks", 0, 10, 64));

        List<Slice> surplus = KIT_FOR.kit(activity, true).surplus(inventory, VALUES);
        List<Stack> left = remove(inventory, surplus);

        for (Stack stack : left) {
            if (stack != null) {
                assertEquals(
                        true,
                        "bread".equals(stack.item()) || isFood(stack.item()),
                        "у ребёнка остаётся только еда, а в слоте '" + stack.item()
                                + "' осталось что-то иное: ребёнок не носит инструменты и припасы");
            }
        }
    }

    private static boolean isFood(String item) {
        return "bread".equals(item) || "cooked_beef".equals(item) || "apple".equals(item);
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
