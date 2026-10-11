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
    private static final BaseKit KIT = BaseKit.load();

    @Provide
    Arbitrary<List<Stack>> inventories() {
        Arbitrary<String> items = Arbitraries.of("cobblestone", "dirt", "iron_ingot", "iron_nugget", "bread",
                "diamond", "oak_log", "torch", "iron_pickaxe", "crafting_table", "bucket");
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
                List.of("bread"),
                List.of("diamond"),
                List.of("cobblestone", "dirt"));
    }

    private static int maxStackOf(String item) {
        return item.equals("iron_pickaxe") ? 1 : 64;
    }

    @Property
    void decide_DroppedSlot_HasGiveAway(@ForAll("inventories") List<Stack> inventory,
            @ForAll("incoming") Stack incoming, @ForAll("protections") List<String> patterns) {
        OverflowPolicy.Decision decision = OverflowPolicy.decide(inventory, incoming, VALUES, patterns, KIT);

        if (decision.action() != OverflowPolicy.Action.DROP) {
            return;
        }
        int give = Protected.of(patterns, inventory, KIT, VALUES).giveAway(decision.slot());
        assertTrue(give > 0,
                "Выброшен слот " + decision.slot() + " инвентаря " + inventory + " не имеет излишка: "
                        + "политика отдала бы в контейнер то, что базовый набор придержал. Излишек " + give
                        + ", решено отдать " + decision.count());
        assertEquals(give, decision.count(),
                "Отданное из слота " + decision.slot() + " (" + decision.count() + ") равно его излишку ("
                        + give + "): политика режет только лишнее, иначе набор теряет свои предметы");
    }

    @Property
    void decide_SameInputs_GiveSameDecision(@ForAll("inventories") List<Stack> inventory,
            @ForAll("incoming") Stack incoming, @ForAll("protections") List<String> patterns) {
        assertEquals(OverflowPolicy.decide(inventory, incoming, VALUES, patterns, KIT),
                OverflowPolicy.decide(inventory, incoming, VALUES, patterns, KIT),
                "Решение политики детерминировано: на одном и том же инвентаре дух должен принимать "
                        + "одно и то же решение, иначе подбор дропа зависит от порядка вызовов. "
                        + "Инвентарь " + inventory + ", кучка " + incoming);
    }

    @Property
    void decide_AfterDropAndTake_EveryKitRuleKeepsItsQuotaAndValueGrows(@ForAll("inventories") List<Stack> inventory,
            @ForAll("incoming") Stack incoming, @ForAll("protections") List<String> patterns) {
        OverflowPolicy.Decision decision = OverflowPolicy.decide(inventory, incoming, VALUES, patterns, KIT);
        if (decision.action() != OverflowPolicy.Action.DROP) {
            return;
        }
        List<Stack> after = dropGivenAway(inventory, decision);
        for (KitRule rule : KIT.rules()) {
            int before = countOfRule(inventory, rule);
            int left = countOfRule(after, rule);
            assertTrue(left >= Math.min(before, rule.keep()),
                    "Правило набора " + rule.match() + rule.category() + " с квотой " + rule.keep()
                            + " до обмена имело " + before + " предметов, после — " + left
                            + ": набор обязан придержать своё, а не отдавать его вместе с лишком. "
                            + "Инвентарь " + inventory + ", кучка " + incoming);
        }
        int incomingValue = VALUES.stackValue(incoming);
        assertTrue(totalValue(after) + incomingValue > totalValue(inventory),
                "Ценность того, что осталось, плюс ценность кучки выше прежней: было "
                        + totalValue(inventory) + ", осталось " + totalValue(after) + " и пришло "
                        + incomingValue + ". Обмен, делающий духа беднее, — дефект политики. "
                        + "Инвентарь " + inventory + ", кучка " + incoming);
    }

    @Property
    void decide_AfterDropAndTake_InventoryFitsInSlots(@ForAll("inventories") List<Stack> inventory,
            @ForAll("incoming") Stack incoming, @ForAll("protections") List<String> patterns) {
        OverflowPolicy.Decision decision = OverflowPolicy.decide(inventory, incoming, VALUES, patterns, KIT);
        if (decision.action() != OverflowPolicy.Action.DROP) {
            return;
        }
        List<Stack> after = dropGivenAway(inventory, decision);

        assertTrue(after.size() <= OverflowPolicy.INVENTORY_SLOTS,
                "После обмена слотов не больше " + OverflowPolicy.INVENTORY_SLOTS + ": стало "
                        + after.size() + ". Инвентарь " + inventory + ", кучка " + incoming);
        assertTrue(VALUES.stackValue(incoming) > VALUES.valueOf(inventory.get(decision.slot()).item())
                        * decision.count(),
                "Кучка берётся, только если она дороже отдаваемого: ценность кучки "
                        + VALUES.stackValue(incoming) + " против отданного "
                        + VALUES.valueOf(inventory.get(decision.slot()).item()) * decision.count());
    }

    private static int countOfRule(List<Stack> inventory, KitRule rule) {
        int total = 0;
        for (Stack stack : inventory) {
            if (stack != null && rule.covers(stack.item(), VALUES)) {
                total += stack.count();
            }
        }
        return total;
    }

    private static List<Stack> dropGivenAway(List<Stack> inventory, OverflowPolicy.Decision decision) {
        List<Stack> after = new ArrayList<>(inventory);
        Stack slot = after.get(decision.slot());
        int left = slot.count() - decision.count();
        after.set(decision.slot(), left == 0 ? null : slot.withCount(left));
        return after;
    }

    private static int totalValue(List<Stack> inventory) {
        int total = 0;
        for (Stack stack : inventory) {
            if (stack != null) {
                total += VALUES.stackValue(stack);
            }
        }
        return total;
    }
}
