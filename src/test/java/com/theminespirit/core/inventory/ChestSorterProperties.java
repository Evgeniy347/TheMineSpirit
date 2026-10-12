package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class ChestSorterProperties {
    private static final ValueTable VALUES = ValueTable.load();

    @Provide
    Arbitrary<List<Stack>> slots() {
        Arbitrary<String> items = Arbitraries.of("cobblestone", "dirt", "oak_planks", "oak_log", "iron_ingot",
                "gold_ingot", "bread", "cooked_beef", "torch", "iron_pickaxe", "iron_sword", "iron_helmet",
                "diamond_chestplate", "bucket", "crafting_table", "stick");
        Arbitrary<Stack> stack = Combinators.combine(
                items,
                Arbitraries.integers().between(0, 2),
                Arbitraries.integers().between(1, 3))
                .as((item, variant, count) -> new Stack(item, variant, Math.min(count, maxStackOf(item)),
                        maxStackOf(item)));
        return stack.list().ofMaxSize(30);
    }

    private static int maxStackOf(String item) {
        if (item.endsWith("pickaxe") || item.endsWith("sword") || item.endsWith("helmet")
                || item.endsWith("chestplate") || item.equals("bucket") || item.equals("crafting_table")) {
            return 1;
        }
        return 64;
    }

    @Property(tries = 200)
    void sort_Slots_PreserveTotalCountPerItemAndVariant(@ForAll("slots") List<Stack> slots) {
        List<Stack> layout = ChestSorter.sort(slots, VALUES);
        Map<String, Integer> before = countsByKind(slots);
        Map<String, Integer> after = countsByKind(layout);

        for (String kind : before.keySet()) {
            assertEquals(before.get(kind), after.get(kind),
                    "Сортировка только переставляет и сливает стопки: пары «предмет+вариант» '" + kind
                            + "' было " + before.get(kind) + ", стало " + after.get(kind) + ". Исходные слоты "
                            + slots + ", раскладка " + layout);
        }
    }

    @Property(tries = 200)
    void sort_Layout_NeverExceedsMaxStack(@ForAll("slots") List<Stack> slots) {
        for (Stack stack : ChestSorter.sort(slots, VALUES)) {
            assertTrue(stack.count() <= stack.maxStack(),
                    "Стопка " + stack + " не может быть больше максимума " + stack.maxStack() + ": "
                            + "в игре такая стопка не поместится в слот, и предметы потеряются. "
                            + "Исходные слоты " + slots);
        }
    }

    @Property(tries = 200)
    void sort_Layout_UsesMinimalSlotCount(@ForAll("slots") List<Stack> slots) {
        List<Stack> layout = ChestSorter.sort(slots, VALUES);
        int minimal = 0;
        for (Map.Entry<String, Integer> kind : countsByKind(slots).entrySet()) {
            int max = maxStackOfKind(slots, kind.getKey());
            minimal += (int) Math.ceil((double) kind.getValue() / max);
        }

        assertEquals(minimal, layout.size(),
                "Число занятых слотов обязано быть минимальным (" + minimal + "): раскладка заняла "
                        + layout.size() + ". Исходные слоты " + slots + ", раскладка " + layout);
    }

    @Property(tries = 200)
    void sort_AppliedTwice_GivesSameLayout(@ForAll("slots") List<Stack> slots) {
        List<Stack> once = ChestSorter.sort(slots, VALUES);

        assertEquals(once, ChestSorter.sort(once, VALUES),
                "Сортировка идемпотентна: повторная сортировка раскладки не меняет её. "
                        + "Иначе sort_chest никогда не закончится, а разгрузка каждый раз "
                        + "перекладывала бы сундук. Исходные слоты " + slots);
    }

    @Property(tries = 200)
    void sort_ShuffledSlots_GiveSameLayout(@ForAll("slots") List<Stack> slots) {
        List<Stack> expected = ChestSorter.sort(slots, VALUES);
        List<Stack> shuffled = new ArrayList<>(slots);
        Collections.reverse(shuffled);

        assertEquals(expected, ChestSorter.sort(shuffled, VALUES),
                "Раскладка не зависит от порядка слотов: сундук, в котором предметы сложили в другом "
                        + "порядке, обязан раскладываться так же. Исходные слоты " + slots);
    }

    @Property(tries = 200)
    void sort_Layout_KeepsEmptySlotsAtTheEnd(@ForAll("slots") List<Stack> slots) {
        List<Stack> layout = ChestSorter.sort(slots, VALUES);

        assertEquals(0, layout.stream().filter(Objects::isNull).count(),
                "В раскладке нет пустых слотов: пустые остаются в конце контейнера, а внутри "
                        + "раскладки пустого слота быть не может. Раскладка " + layout);
        assertTrue(layout.size() <= slots.size(),
                "Раскладка не длиннее исходного контейнера: слияние стопок только сокращает число "
                        + "занятых слотов. Слотов " + slots.size() + ", в раскладке " + layout.size());
    }

    private static Map<String, Integer> countsByKind(List<Stack> slots) {
        Map<String, Integer> counts = new HashMap<>();
        for (Stack stack : slots) {
            counts.merge(stack.item() + "#" + stack.variant(), stack.count(), Integer::sum);
        }
        return counts;
    }

    private static int maxStackOfKind(List<Stack> slots, String kind) {
        String item = kind.substring(0, kind.indexOf('#'));
        return maxStackOf(item);
    }
}
