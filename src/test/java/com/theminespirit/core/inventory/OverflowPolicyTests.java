package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OverflowPolicyTests {
    private static final ValueTable VALUES = ValueTable.load();
    private static final BaseKit KIT = BaseKit.load();

    @ParameterizedTest(name = "{0} → действие {4}")
    @CsvSource(delimiter = '|', value = {
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
            "нужная еда защищена шаблоном|bread:16*35;cobblestone:64|diamond:1|bread|DROP|35|64"})
    void decide_TableOfSituations_MatchesExpected(String situation, String inventorySpec, String incomingSpec,
            String protectedSpec, String expectedAction, int expectedSlot, int expectedCount) {
        List<Stack> inventory = inventory(inventorySpec);
        String protectionSpec = protectedSpec == null ? "" : protectedSpec.trim();
        Protected protection = protectionSpec.isEmpty()
                ? Protected.nothing()
                : Protected.of(List.of(protectionSpec), inventory);

        OverflowPolicy.Decision decision = OverflowPolicy.decide(inventory, stack(incomingSpec), VALUES, protection);

        assertEquals(OverflowPolicy.Action.valueOf(expectedAction.trim()), decision.action(),
                "Ситуация '" + situation + "': действие политики должно совпасть с ожидаемым, иначе дух "
                        + "либо потеряет ценную кучку, либо выбросит нужное. Инвентарь " + inventorySpec
                        + ", кучка " + incomingSpec + ", защита '" + protectionSpec + "', причина "
                        + decision.reason());
        if (decision.action() == OverflowPolicy.Action.DROP) {
            assertEquals(expectedSlot, decision.slot(),
                    "Ситуация '" + situation + "': выбросить должен самый дешёвый незащищённый слот "
                            + expectedSlot);
            assertEquals(expectedCount, decision.count(),
                    "Ситуация '" + situation + "': выбранный слот выбрасывается целиком");
        }
    }

    @Test
    void decide_InventoryHasFreeSlot_TakesWithoutDropping() {
        List<Stack> inventory = List.of(
                new Stack("cobblestone", 0, 1, 64),
                new Stack("iron_ingot", 0, 5, 64));

        OverflowPolicy.Decision decision = OverflowPolicy.decide(inventory, new Stack("diamond", 0, 1, 64),
                VALUES, Protected.nothing());

        assertEquals(OverflowPolicy.Action.TAKE_INTO_FREE_SLOT, decision.action(),
                "Свободный слот принимает кучку без выброса: выбрасывать что-то при месте в инвентаре "
                        + "значит терять предметы зря");
    }

    @Test
    void decide_PartialStackOfSameKind_TakesWithoutDropping() {
        List<Stack> inventory = new ArrayList<>();
        for (int slot = 0; slot < OverflowPolicy.INVENTORY_SLOTS - 1; slot++) {
            inventory.add(new Stack("cobblestone", 0, 64, 64));
        }
        inventory.add(new Stack("diamond", 0, 63, 64));

        OverflowPolicy.Decision decision = OverflowPolicy.decide(inventory, new Stack("diamond", 0, 1, 64),
                VALUES, Protected.nothing());

        assertEquals(OverflowPolicy.Action.TAKE_INTO_FREE_SLOT, decision.action(),
                "Алмаз добирается до полной стопки своего же слота: выброс ради кучки, которая помещается "
                        + "частично, уничтожил бы лишний предмет");
    }

    @Test
    void decide_BaseKitQuota_DropsSurplusSlotsAndKeepsQuota() {
        List<Stack> inventory = inventory("bread:16;cobblestone:64*35");

        OverflowPolicy.Decision decision = OverflowPolicy.decide(inventory, new Stack("diamond", 0, 1, 64),
                VALUES, Protected.withBaseKit(List.of(), KIT, inventory, VALUES));

        assertEquals(OverflowPolicy.Action.DROP, decision.action(),
                "Квота набора не мешает отдать излишек: булыжник сверх 32 уходит в контейнер, "
                        + "хлеб остаётся на месте");
        assertEquals(35, decision.slot(),
                "Из равных стопок булыжника выбрасывается слот с наибольшим номером (35): хлеб в пределах "
                        + "квоты 16 защищён набором, весь булыжник — излишек");
        assertEquals(64, decision.count(),
                "Булыжник выбрасывается стопкой целиком: политика работает слотами, а не частями");
    }

    @Test
    void decide_ItemBelowKitQuota_IsNeverDropped() {
        List<Stack> inventory = inventory("bread:16;cobblestone:64*35");
        Protected protection = Protected.withBaseKit(List.of(), KIT, inventory, VALUES);

        assertEquals(false, protection.shieldsSlot(1),
                "Слот булыжника набора не защищён: булыжника 35 стопок, квота 32, лишки есть");
        assertEquals(true, protection.shieldsSlot(0),
                "Слот хлеба защищён набором: ровно 16 хлеба при квоте 16, отдавать нечего");
    }

    @Test
    void decide_PlanNeedsProtectedByPattern_AreNotDropped() {
        List<Stack> inventory = inventory("oak_planks:64*18;cobblestone:64*18");

        OverflowPolicy.Decision decision = OverflowPolicy.decide(inventory, new Stack("diamond", 0, 1, 64),
                VALUES, Protected.of(List.of("oak_planks"), inventory));

        assertEquals(OverflowPolicy.Action.DROP, decision.action(),
                "Доски, нужные плану (крафт), не выбрасываются: защита «нужного плану» сильнее дешевизны "
                        + "предмета");
        assertEquals(35, decision.slot(),
                "Выбрасывается булыжник с наибольшего номера, а не доски плана: доски дешевле, "
                        + "но защищены шаблоном");
    }

    @Test
    void decide_DropAndTake_InventoryFitsInSlotsAndKeepsValue() {
        List<Stack> inventory = new ArrayList<>();
        for (int slot = 0; slot < OverflowPolicy.INVENTORY_SLOTS; slot++) {
            inventory.add(new Stack("cobblestone", 0, 64, 64));
        }
        Stack incoming = new Stack("diamond", 0, 1, 64);
        int before = totalValue(inventory);

        OverflowPolicy.Decision decision = OverflowPolicy.decide(inventory, incoming, VALUES, Protected.nothing());
        List<Stack> after = drop(inventory, decision, incoming);

        assertEquals(OverflowPolicy.INVENTORY_SLOTS, after.size(),
                "После выброса и приёма инвентарь снова ровно " + OverflowPolicy.INVENTORY_SLOTS
                        + " слотов: кучка занимает освободившееся место, а не добавляет слот сверх лимита");
        assertTrue(totalValue(after) > before,
                "Суммарная ценность инвентаря после подмены выросла: была " + before + ", стала "
                        + totalValue(after) + ". Пока она не растёт, обмен по ценности — не грабёж");
    }

    @Test
    void decide_SameInputs_GiveSameDecision() {
        List<Stack> inventory = List.of(
                new Stack("cobblestone", 0, 64, 64),
                new Stack("dirt", 0, 64, 64),
                new Stack("iron_ingot", 0, 1, 64));
        Stack incoming = new Stack("diamond", 0, 1, 64);

        OverflowPolicy.Decision first = OverflowPolicy.decide(inventory, incoming, VALUES, Protected.nothing());
        OverflowPolicy.Decision second = OverflowPolicy.decide(inventory, incoming, VALUES, Protected.nothing());

        assertEquals(first, second,
                "Одинаковый вход даёт одинаковое решение: политика работает на серверном тике, "
                        + "и разного выбора на одном и том же инвентаре быть не должно");
    }

    @Test
    void decide_AllSlotsProtected_ReasonNamesFullInventory() {
        List<Stack> inventory = inventory("iron_pickaxe:1*18;bread:16*18");

        OverflowPolicy.Decision decision = OverflowPolicy.decide(inventory, new Stack("diamond", 0, 1, 64),
                VALUES, Protected.of(List.of("iron_pickaxe", "bread"), inventory));

        assertEquals(OverflowPolicy.Action.REFUSE, decision.action(),
                "Когда всё защищено, кучка не берётся: базовый набор и нужное плану важнее находки. "
                        + "Причина: " + decision.reason());
        assertTrue(decision.reason().contains("инвентарь полон"),
                "Причина отказа начинается с «инвентарь полон»: коллектор показывает этот текст как итог "
                        + "collect_drops. Фактическая причина: " + decision.reason());
    }

    @Test
    void decide_RefuseReason_NamesBothStacks() {
        List<Stack> inventory = inventory("diamond:1*36");

        OverflowPolicy.Decision decision = OverflowPolicy.decide(inventory, stack("cobblestone:64"), VALUES,
                Protected.nothing());

        assertEquals(OverflowPolicy.Action.REFUSE, decision.action(),
                "Дешёвая кучка не вытесняет алмаз: причина отказа должна объяснять, почему");
        assertTrue(decision.reason().contains("cobblestone") && decision.reason().contains("diamond"),
                "Причина отказа называет обе стопки — новую и самую дешёвую в инвентаре: по ней игрок "
                        + "понимает, что дух не стал брать мусор. Фактическая причина: " + decision.reason());
    }

    private static List<Stack> inventory(String spec) {
        List<Stack> slots = new ArrayList<>();
        for (String part : spec.split(";")) {
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
        String[] parts = spec.split("#");
        String[] name = parts[0].split(":");
        int variant = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        int count = Integer.parseInt(name[1]);
        int maxStack = name.length > 2 ? Integer.parseInt(name[2]) : 64;
        return new Stack(name[0], variant, count, maxStack);
    }

    private static int totalValue(List<Stack> inventory) {
        int total = 0;
        for (Stack stack : inventory) {
            total += VALUES.stackValue(stack);
        }
        return total;
    }

    private static List<Stack> drop(List<Stack> inventory, OverflowPolicy.Decision decision, Stack incoming) {
        List<Stack> after = new ArrayList<>(inventory);
        after.set(decision.slot(), incoming);
        return after;
    }
}
