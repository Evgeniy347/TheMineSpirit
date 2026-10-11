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

class OverflowPolicyProperties {
    private static final ValueTable VALUES = ValueTable.load();

    @Provide
    Arbitrary<List<Stack>> inventories() {
        Arbitrary<String> items = Arbitraries.of("cobblestone", "dirt", "iron_ingot", "iron_nugget", "bread",
                "diamond", "oak_log", "torch", "iron_pickaxe");
        Arbitrary<Stack> stack = Combinators.combine(
                items,
                Arbitraries.integers().between(0, 1),
                Arbitraries.integers().between(1, 3))
                .as((item, variant, count) -> new Stack(item, variant, count, maxStackOf(item)));
        return stack.list().ofMaxSize(36);
    }

    @Provide
    Arbitrary<Stack> incoming() {
        Arbitrary<String> items = Arbitraries.of("cobblestone", "dirt", "iron_ingot", "bread", "diamond",
                "oak_log", "torch", "iron_pickaxe", "gold_ingot");
        return Combinators.combine(
                items,
                Arbitraries.integers().between(0, 1),
                Arbitraries.integers().between(1, 3))
                .as((item, variant, count) -> new Stack(item, variant, count, maxStackOf(item)));
    }

    @Provide
    Arbitrary<List<String>> protections() {
        return Arbitraries.of(
                List.of(),
                List.of("iron_pickaxe"),
                List.of("iron_pickaxe", "torch"),
                List.of("*_planks", "bread"),
                List.of("diamond"),
                List.of("cobblestone", "dirt"));
    }

    private static int maxStackOf(String item) {
        return item.equals("iron_pickaxe") ? 1 : 64;
    }

    @Property
    void decide_DroppedSlot_IsNeverProtected(@ForAll("inventories") List<Stack> inventory,
            @ForAll("incoming") Stack incoming, @ForAll("protections") List<String> patterns) {
        Protected protection = Protected.of(patterns, inventory);
        OverflowPolicy.Decision decision = OverflowPolicy.decide(inventory, incoming, VALUES, protection);

        if (decision.action() != OverflowPolicy.Action.DROP) {
            return;
        }
        assertTrue(!protection.shieldsSlot(decision.slot()),
                "Выброшен слот " + decision.slot() + " (" + inventory.get(decision.slot())
                        + ") защищён паттернами " + patterns + ": защита существует ровно для того, "
                        + "чтобы нужное плану и базовый набор не вылетали из инвентаря. Инвентарь "
                        + inventory);
    }

    @Property
    void decide_SameInputs_GiveSameDecision(@ForAll("inventories") List<Stack> inventory,
            @ForAll("incoming") Stack incoming, @ForAll("protections") List<String> patterns) {
        Protected first = Protected.of(patterns, inventory);
        Protected second = Protected.of(patterns, inventory);

        assertEquals(OverflowPolicy.decide(inventory, incoming, VALUES, first),
                OverflowPolicy.decide(inventory, incoming, VALUES, second),
                "Решение политики детерминировано: на одном и том же инвентаре дух должен принимать "
                        + "одно и то же решение, иначе подбор дропа зависит от порядка вызовов. "
                        + "Инвентарь " + inventory + ", кучка " + incoming);
    }

    @Property
    void decide_AfterDropAndTake_InventoryFitsInSlotsAndKeepsValue(@ForAll("inventories") List<Stack> inventory,
            @ForAll("incoming") Stack incoming, @ForAll("protections") List<String> patterns) {
        Protected protection = Protected.of(patterns, inventory);
        OverflowPolicy.Decision decision = OverflowPolicy.decide(inventory, incoming, VALUES, protection);
        if (decision.action() != OverflowPolicy.Action.DROP) {
            return;
        }
        List<Stack> after = new ArrayList<>(inventory);
        after.set(decision.slot(), incoming);
        after.removeIf(stack -> stack == null);

        assertTrue(after.size() <= OverflowPolicy.INVENTORY_SLOTS,
                "После обмена инвентарь занимает не больше " + OverflowPolicy.INVENTORY_SLOTS
                        + " слотов, а занял " + after.size() + ": иначе дух вышел бы за пределы "
                        + "инвентаря и перенесённая кучка потерялась бы. Инвентарь " + inventory);
        assertTrue(totalValue(after) >= totalValue(inventory),
                "Суммарная ценность инвентаря не уменьшилась: было " + totalValue(inventory) + ", стало "
                        + totalValue(after) + ". Обмен по ценности, который делает духа беднее, — дефект "
                        + "политики. Инвентарь " + inventory + ", кучка " + incoming);
    }

    private static int totalValue(List<Stack> inventory) {
        int total = 0;
        for (Stack stack : inventory) {
            total += VALUES.stackValue(stack);
        }
        return total;
    }
}
