package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OverflowPolicyTests {
    private static final ValueTable VALUES = ValueTable.load();
    private static final BaseKit KIT = BaseKit.load();

    @ParameterizedTest(name = "{0} → действие {4}")
    @CsvSource(
            delimiter = '|',
            value = {
                "свободный слот принимает без выброса|cobblestone:1|diamond:1| |TAKE_INTO_FREE_SLOT|-1|0",
                "частичная стопка того же вида добирается|cobblestone:64*35;diamond:63|diamond:1| |TAKE_INTO_FREE_SLOT|-1|0",
                "полный инвентарь и дороже кучка|cobblestone:64*36|diamond:1| |DROP|35|64",
                "самый дешёвый слот среди разных|cobblestone:64*35;dirt:64|diamond:1| |DROP|35|64",
                "новая кучка дороже дорогих слотов|iron_ingot:1*36|diamond:1| |DROP|35|1",
                "равная ценность стопок при разной цене за штуку|iron_ingot:1*35;iron_nugget:10|diamond:1| |DROP|35|10",
                "новая кучка дешевле самой дешёвой|diamond:1*36|cobblestone:64| |REFUSE|-1|-1",
                "ничья ценностей не берётся|cobblestone:64*36|cobblestone:32| |REFUSE|-1|-1",
                "новое дешевле дорогих слотов|iron_ingot:1*36|coal:1| |REFUSE|-1|-1",
                "всё защищено шаблоном|cobblestone:64*36|diamond:1|*|REFUSE|-1|-1",
                "защищён только дешёвый слот|cobblestone:64*35;dirt:64|diamond:1|cobblestone|DROP|35|64",
                "незащищён только дорогой слот|cobblestone:64*35;diamond:1|cobblestone:1|cobblestone|REFUSE|-1|-1",
                "нужная еда защищена шаблоном|bread:16*36|diamond:1|bread|REFUSE|-1|-1",
                "частичный излишек слота не освобождает слот|bread:20;iron_ingot:64*35|diamond:1| |REFUSE|-1|-1",
                "защищён дорогой слот|iron_ingot:1*36|diamond:1|iron_ingot|REFUSE|-1|-1",
                "кучка того же предмета при полном инвентаре|dirt:64*36|dirt:64| |REFUSE|-1|-1",
                "лучший меч остаётся при духе|iron_sword:1*36|diamond:1| |DROP|35|1",
                "факелы нужны плану|torch:24*36|iron_ingot:1|torch|REFUSE|-1|-1",
                "лишний хлеб отдельной стопкой уходит|bread:16;bread:4;iron_ingot:64*34|diamond:1| |DROP|1|4",
                "кирка вне постоянной части уходит|iron_pickaxe:1*36|diamond:1| |DROP|35|1",
                "второй лук уходит при переполнении|bow:1*36|diamond:1| |DROP|35|1"
            })
    void decide_TableOfSituations_MatchesExpected(
            String situation,
            String inventorySpec,
            String incomingSpec,
            String protectedSpec,
            String expectedAction,
            int expectedSlot,
            int expectedCount) {
        List<Stack> inventory = inventory(inventorySpec);
        String protectionSpec = protectedSpec == null ? "" : protectedSpec.trim();
        List<String> planNeeds = protectionSpec.isEmpty() ? List.of() : List.of(protectionSpec);

        OverflowPolicy.Decision decision =
                OverflowPolicy.decide(inventory, stack(incomingSpec), VALUES, planNeeds, KIT);

        assertEquals(
                OverflowPolicy.Action.valueOf(expectedAction.trim()),
                decision.action(),
                "Ситуация '" + situation + "': действие политики должно совпасть с ожидаемым, иначе дух "
                        + "либо потеряет ценную кучку, либо выбросит нужное. Инвентарь " + inventorySpec
                        + ", кучка " + incomingSpec + ", нужное плану '" + protectionSpec + "', причина "
                        + decision.reason() + ". При смене decide проверь действие в ситуации '" + situation + "'");
        if (decision.action() == OverflowPolicy.Action.DROP) {
            assertEquals(
                    expectedSlot,
                    decision.slot(),
                    "Ситуация '" + situation + "': выбросить должен самый дешёвый незащищённый слот " + expectedSlot
                            + ", иначе выбор слота случаен. При смене decide проверь слот в ситуации '" + situation
                            + "'");
            assertEquals(
                    expectedCount,
                    decision.count(),
                    "Ситуация '" + situation + "': выбрасывается слот целиком и только когда весь он "
                            + "излишек, иначе частичный выброс оставляет слот занятым и кучке некуда деться. "
                            + "При смене decide проверь размер выброса");
            assertTrue(
                    expectedCount == inventory.get(decision.slot()).count(),
                    "Ситуация '" + situation + "': выбранный слот выбрасывается целиком (" + expectedCount
                            + "), в нём лежало " + inventory.get(decision.slot())
                            + ", иначе слот останется занятым. При смене decide проверь полноту выброса");
        }
    }

    @Test
    void decide_ManyOfOneItemOnBase_DropsItBeforeRarerLogs() {
        OverflowTuning tuning = new OverflowTuning("тест: склад забит железом", 32, 0.05);
        List<Stack> inventory = inventory("iron_ingot:64*18;oak_log:64*18");

        OverflowPolicy.Decision decision = OverflowPolicy.decide(
                inventory,
                new Stack("diamond", 0, 1, 64),
                VALUES,
                List.of(),
                KIT,
                Map.of("iron_ingot", 10_000),
                tuning);

        assertEquals(
                OverflowPolicy.Action.DROP,
                decision.action(),
                "Алмаз дороже любой из стопок, поэтому обмен возможен, иначе выбор дропа врёт: причина "
                        + decision.reason() + ". При смене decide проверь действие при складе железа");
        assertEquals(
                "iron_ingot",
                inventory.get(decision.slot()).item(),
                "При огромном запасе железа на базе его ценность падает ниже брёвен, и выбрасывается "
                        + "именно железо, а редкое дерево остаётся у духа, иначе выбросят редкое. Решение: "
                        + decision.reason() + ". При смене decide проверь выбор железа");
    }

    @Test
    void decide_StockOfProtectedItems_NeverDropsTheNorm() {
        OverflowTuning tuning = new OverflowTuning("тест: склад забит едой", 8, 0.05);
        List<Stack> inventory = inventory("bread:16;iron_ingot:64*35");

        OverflowPolicy.Decision decision = OverflowPolicy.decide(
                inventory,
                new Stack("diamond", 0, 1, 64),
                VALUES,
                List.of(),
                KIT,
                Map.of("bread", 10_000, "iron_ingot", 10_000),
                tuning);

        assertEquals(
                OverflowPolicy.Action.DROP,
                decision.action(),
                "Излишек железа вне квоты набора уходит, иначе излишек зависнет в инвентаре: причина "
                        + decision.reason() + ". При смене decide проверь выброс излишка");
        assertTrue(
                !"bread".equals(inventory.get(decision.slot()).item()),
                "Норма еды не выбрасывается даже когда её на базе много: хлеб защищён базовым "
                        + "набором, а складская цена не может отнять квоту. При смене decide проверь норму еды");
    }

    @Test
    void decide_ManyOfIncomingOnBase_RefusesToSwapForScarceItem() {
        OverflowTuning tuning = new OverflowTuning("тест: склад забит алмазами", 32, 0.05);
        List<Stack> inventory = inventory("oak_log:64*36");

        OverflowPolicy.Decision decision = OverflowPolicy.decide(
                inventory, new Stack("diamond", 0, 1, 64), VALUES, List.of(), KIT, Map.of("diamond", 10_000), tuning);

        assertEquals(
                OverflowPolicy.Action.REFUSE,
                decision.action(),
                "Алмазов на базе много, дерева нет: дешёвый по складу алмаз не вытесняет редкое бревно, "
                        + "иначе дерево пропадёт: причина " + decision.reason()
                        + ". При смене decide проверь отказ при складе алмазов");
    }

    @Test
    void decide_InventoryHasFreeSlot_TakesWithoutDropping() {
        List<Stack> inventory = List.of(new Stack("cobblestone", 0, 1, 64), new Stack("iron_ingot", 0, 5, 64));

        OverflowPolicy.Decision decision =
                OverflowPolicy.decide(inventory, new Stack("diamond", 0, 1, 64), VALUES, List.of(), KIT);

        assertEquals(
                OverflowPolicy.Action.TAKE_INTO_FREE_SLOT,
                decision.action(),
                "Свободный слот принимает кучку без выброса: иначе выбрасывать что-то при месте в инвентаре "
                        + "значит терять предметы зря. При смене decide проверь свободный слот");
    }

    @Test
    void decide_PartialStackOfSameKind_TakesWithoutDropping() {
        List<Stack> inventory = new ArrayList<>();
        for (int slot = 0; slot < OverflowPolicy.INVENTORY_SLOTS - 1; slot++) {
            inventory.add(new Stack("cobblestone", 0, 64, 64));
        }
        inventory.add(new Stack("diamond", 0, 63, 64));

        OverflowPolicy.Decision decision =
                OverflowPolicy.decide(inventory, new Stack("diamond", 0, 1, 64), VALUES, List.of(), KIT);

        assertEquals(
                OverflowPolicy.Action.TAKE_INTO_FREE_SLOT,
                decision.action(),
                "Алмаз добирается до полной стопки своего же слота: выброс ради кучки, которая помещается "
                        + "частично, иначе уничтожил бы лишний предмет. При смене decide проверь доливку");
    }

    @Test
    void decide_DropAndTake_RaisesTotalValue() {
        List<Stack> inventory = inventory("cobblestone:64*36");
        int before = totalValue(inventory);

        OverflowPolicy.Decision decision =
                OverflowPolicy.decide(inventory, new Stack("diamond", 0, 1, 64), VALUES, List.of(), KIT);
        List<Stack> after = dropOnlyGivenAway(inventory, decision);
        int incomingValue = VALUES.stackValue(new Stack("diamond", 0, 1, 64));
        int droppedValue = VALUES.valueOf(inventory.get(decision.slot()).item()) * decision.count();

        assertEquals(
                OverflowPolicy.Action.DROP,
                decision.action(),
                "Булыжник дешевле алмаза, поэтому политика отдаёт лишнее и берёт алмаз, иначе обмен не "
                        + "состоится: причина " + decision.reason() + ". При смене decide проверь обмен с булыжником");
        assertTrue(
                after.size() <= OverflowPolicy.INVENTORY_SLOTS,
                "После обмена слотов не больше " + OverflowPolicy.INVENTORY_SLOTS + ": стало " + after.size()
                        + ", иначе лишний слот вышел бы за пределы инвентаря. При смене decide проверь число слотов");
        assertTrue(
                totalValue(after) + incomingValue > before,
                "Сумма ценности того, что осталось, и подобранного выше прежней: было " + before
                        + ", осталось " + totalValue(after) + " и пришло " + incomingValue
                        + " (отдано ценностью " + droppedValue + "). Обмен, делающий духа беднее, — дефект, "
                        + "иначе дух обеднеет. При смене decide проверь рост ценности");
    }

    private static List<Stack> dropOnlyGivenAway(List<Stack> inventory, OverflowPolicy.Decision decision) {
        List<Stack> after = new ArrayList<>(inventory);
        Stack slot = after.get(decision.slot());
        int left = slot.count() - decision.count();
        after.set(decision.slot(), left == 0 ? null : slot.withCount(left));
        return after;
    }

    private static List<Stack> inventory(String spec) {
        List<Stack> slots = new ArrayList<>();
        for (String part : spec.split(";", -1)) {
            if (part.isBlank()) {
                continue;
            }
            int repeat = part.indexOf('*');
            if (repeat < 0) {
                slots.add(stack(part));
                continue;
            }
            String single = part.substring(0, repeat);
            int times = Integer.parseInt(part.substring(repeat + 1));
            for (int index = 0; index < times; index++) {
                slots.add(stack(single));
            }
        }
        return slots;
    }

    private static Stack stack(String spec) {
        String[] parts = spec.split("#", -1);
        String[] name = parts[0].split(":", -1);
        int variant = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        int count = Integer.parseInt(name[1]);
        int maxStack = name.length > 2 ? Integer.parseInt(name[2]) : 64;
        return new Stack(name[0], variant, count, maxStack);
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
