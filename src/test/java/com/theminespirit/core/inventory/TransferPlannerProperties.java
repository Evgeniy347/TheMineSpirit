package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.IdFilter;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class TransferPlannerProperties {
    @Provide
    Arbitrary<IdFilter> filters() {
        return Arbitraries.of(
                IdFilter.matchAll(),
                IdFilter.of(List.of("cobblestone")),
                IdFilter.of(List.of("cobblestone", "iron_ingot")),
                IdFilter.of(List.of("*_planks", "bread")),
                IdFilter.of(List.of("nothing_matches_this")));
    }

    @Provide
    Arbitrary<List<Stack>> slots() {
        Arbitrary<String> items = Arbitraries.of("cobblestone", "dirt", "oak_planks", "iron_ingot", "bread",
                "iron_pickaxe", "torch");
        Arbitrary<Stack> stack = Combinators.combine(
                items,
                Arbitraries.integers().between(0, 2),
                Arbitraries.integers().between(1, 3))
                .as((item, variant, count) -> new Stack(item, variant, Math.min(count, maxStackOf(item)),
                        maxStackOf(item)));
        return stack.list().ofMaxSize(8);
    }

    @Provide
    Arbitrary<Integer> limits() {
        return Arbitraries.integers().between(0, 40);
    }

    private static int maxStackOf(String item) {
        return item.equals("iron_pickaxe") ? 1 : 64;
    }

    @Property
    void plan_Moves_PreserveItemsOnBothSides(@ForAll("slots") List<Stack> from, @ForAll("slots") List<Stack> to,
            @ForAll("filters") IdFilter filter, @ForAll("limits") int maxCount) {
        TransferPlan plan = TransferPlanner.plan(from, to, filter, maxCount);
        List<Stack> fromAfter = applyToSource(from, plan);
        List<Stack> toAfter = applyToTarget(to, plan, from);

        for (String item : itemsOf(from, to)) {
            for (int variant : variantsOf(from, to, item)) {
                int before = countOf(from, item, variant) + countOf(to, item, variant);
                int after = countOf(fromAfter, item, variant) + countOf(toAfter, item, variant);
                assertEquals(before, after,
                        "Перенос только перекладывает предметы: '" + item + "' варианта " + variant
                                + " было " + before + " на обеих сторонах, стало " + after
                                + ". План " + plan + " по источнику " + from + " и приёмнику " + to);
            }
        }
    }

    @Property
    void plan_Moves_KeepStacksWithinMaxStack(@ForAll("slots") List<Stack> from, @ForAll("slots") List<Stack> to,
            @ForAll("filters") IdFilter filter, @ForAll("limits") int maxCount) {
        TransferPlan plan = TransferPlanner.plan(from, to, filter, maxCount);
        List<Stack> toAfter = applyToTarget(to, plan, from);

        for (Stack stack : toAfter) {
            if (stack == null) {
                continue;
            }
            assertTrue(stack.count() <= stack.maxStack(),
                    "Стопка " + stack + " в приёмнике не может быть больше максимума " + stack.maxStack()
                            + ": в игре такая стопка не влезет в слот, и предметы потеряются. План по "
                            + "источнику " + from + " и приёмнику " + to);
        }
    }

    @Property
    void plan_Moved_NeverExceedsMaxCount(@ForAll("slots") List<Stack> from, @ForAll("slots") List<Stack> to,
            @ForAll("filters") IdFilter filter, @ForAll("limits") int maxCount) {
        TransferPlan plan = TransferPlanner.plan(from, to, filter, maxCount);

        assertTrue(plan.moved() <= maxCount,
                "Перенесено " + plan.moved() + " при лимите " + maxCount + ": лимит плана — приказ, "
                        + "перенос сверх него отдал бы в контейнер больше, чем просили. Источник " + from
                        + ", приёмник " + to);
    }

    @Property
    void plan_MovesCount_NeverExceedsSlotsOfBothSides(@ForAll("slots") List<Stack> from,
            @ForAll("slots") List<Stack> to, @ForAll("filters") IdFilter filter,
            @ForAll("limits") int maxCount) {
        TransferPlan plan = TransferPlanner.plan(from, to, filter, maxCount);

        assertTrue(plan.moves().size() <= from.size() + to.size(),
                "Ходов " + plan.moves().size() + " не больше слотов источника и приёмника (" + from.size()
                        + " + " + to.size() + "): за тик скрипт делает ограниченное число перемещений, "
                        + "и план длиннее этого предела не выполнится. План " + plan);
    }

    @Property
    void plan_Leftover_EqualsMatchingItemsLeftInSource(@ForAll("slots") List<Stack> from,
            @ForAll("slots") List<Stack> to, @ForAll("filters") IdFilter filter,
            @ForAll("limits") int maxCount) {
        TransferPlan plan = TransferPlanner.plan(from, to, filter, maxCount);
        int matching = 0;
        for (Stack stack : from) {
            if (stack != null && filter.matches(stack.item())) {
                matching += stack.count();
            }
        }

        assertEquals(matching - plan.moved(), plan.leftover(),
                "Остаток равен подходящим предметам источника минус перенесённое (" + matching + " - "
                        + plan.moved() + "): по нему скрипт сообщает «в инвентаре только a из b». "
                        + "Источник " + from + ", фильтр " + filter.patterns());
    }

    @Property
    void plan_Moves_ReferenceSlotsInsideContainers(@ForAll("slots") List<Stack> from,
            @ForAll("slots") List<Stack> to, @ForAll("filters") IdFilter filter,
            @ForAll("limits") int maxCount) {
        TransferPlan plan = TransferPlanner.plan(from, to, filter, maxCount);

        for (Move move : plan.moves()) {
            assertTrue(move.fromSlot() < from.size(),
                    "Слот источника " + move.fromSlot() + " вне инвентаря из " + from.size()
                            + " слотов: перенос из несуществующего слота потерял бы предметы");
            assertTrue(move.toSlot() < to.size(),
                    "Слот приёмника " + move.toSlot() + " вне контейнера из " + to.size()
                            + " слотов: перенос в несуществующий слот уничтожил бы предметы");
        }
    }

    private static List<Stack> applyToSource(List<Stack> from, TransferPlan plan) {
        List<Stack> result = new ArrayList<>(from);
        for (Move move : plan.moves()) {
            Stack stack = result.get(move.fromSlot());
            int left = stack.count() - move.count();
            result.set(move.fromSlot(), left == 0 ? null : stack.withCount(left));
        }
        return result;
    }

    private static List<Stack> applyToTarget(List<Stack> to, TransferPlan plan, List<Stack> from) {
        List<Stack> result = new ArrayList<>(to);
        for (Move move : plan.moves()) {
            Stack source = from.get(move.fromSlot());
            Stack target = result.get(move.toSlot());
            if (target == null) {
                result.set(move.toSlot(),
                        new Stack(source.item(), source.variant(), move.count(), source.maxStack()));
                continue;
            }
            result.set(move.toSlot(), target.withCount(target.count() + move.count()));
        }
        return result;
    }

    private static List<String> itemsOf(List<Stack> first, List<Stack> second) {
        List<String> items = new ArrayList<>();
        for (Stack stack : first) {
            if (stack != null && !items.contains(stack.item())) {
                items.add(stack.item());
            }
        }
        for (Stack stack : second) {
            if (stack != null && !items.contains(stack.item())) {
                items.add(stack.item());
            }
        }
        return items;
    }

    private static List<Integer> variantsOf(List<Stack> first, List<Stack> second, String item) {
        List<Integer> variants = new ArrayList<>();
        for (List<Stack> slots : List.of(first, second)) {
            for (Stack stack : slots) {
                if (stack != null && stack.item().equals(item) && !variants.contains(stack.variant())) {
                    variants.add(stack.variant());
                }
            }
        }
        return variants;
    }

    private static int countOf(List<Stack> slots, String item, int variant) {
        int total = 0;
        for (Stack stack : slots) {
            if (stack != null && stack.item().equals(item) && stack.variant() == variant) {
                total += stack.count();
            }
        }
        return total;
    }
}
