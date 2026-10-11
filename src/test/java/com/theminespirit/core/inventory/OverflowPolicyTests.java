package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
            "нужная еда защищена шаблоном|bread:16*35;cobblestone:64|diamond:1|bread|DROP|35|32",
            "квота еды режет только лишнее|bread:20;iron_ingot:64*35|diamond:1| |DROP|0|4",
            "защищён дорогой слот|iron_ingot:1*35;cobblestone:64|diamond:1|iron_ingot|DROP|35|32",
            "кучка того же предмета при полном инвентаре|dirt:64*36|dirt:64| |REFUSE|-1|-1",
            "лучшая кирка остаётся при духе|iron_pickaxe:1*36|diamond:1| |DROP|35|1",
            "факелы нужны плану|torch:16*35;cobblestone:64|iron_ingot:1|torch|DROP|35|32"})
    void decide_TableOfSituations_MatchesExpected(String situation, String inventorySpec, String incomingSpec,
            String protectedSpec, String expectedAction, int expectedSlot, int expectedCount) {
        List<Stack> inventory = inventory(inventorySpec);
        String protectionSpec = protectedSpec == null ? "" : protectedSpec.trim();
        List<String> planNeeds = protectionSpec.isEmpty() ? List.of() : List.of(protectionSpec);

        OverflowPolicy.Decision decision = OverflowPolicy.decide(inventory, stack(incomingSpec), VALUES, planNeeds,
                KIT);

        assertEquals(OverflowPolicy.Action.valueOf(expectedAction.trim()), decision.action(),
                "Ситуация '" + situation + "': действие политики должно совпасть с ожидаемым, иначе дух "
                        + "либо потеряет ценную кучку, либо выбросит нужное. Инвентарь " + inventorySpec
                        + ", кучка " + incomingSpec + ", нужное плану '" + protectionSpec + "', причина "
                        + decision.reason());
        if (decision.action() == OverflowPolicy.Action.DROP) {
            assertEquals(expectedSlot, decision.slot(),
                    "Ситуация '" + situation + "': выбросить должен самый дешёвый незащищённый слот "
                            + expectedSlot);
            assertEquals(expectedCount, decision.count(),
                    "Ситуация '" + situation + "': выбрасывается ровно излишек слота, а не слот целиком — "
                            + "иначе вместе с лишком уходит то, что набор придержал");
            assertTrue(expectedCount <= inventory.get(decision.slot()).count(),
                    "Ситуация '" + situation + "': из слота " + decision.slot() + " можно отдать не больше, "
                            + "чем в нём лежит (" + inventory.get(decision.slot()) + ")");
        }
    }

    @Test
    void decide_InventoryHasFreeSlot_TakesWithoutDropping() {
        List<Stack> inventory = List.of(
                new Stack("cobblestone", 0, 1, 64),
                new Stack("iron_ingot", 0, 5, 64));

        OverflowPolicy.Decision decision = OverflowPolicy.decide(inventory, new Stack("diamond", 0, 1, 64),
                VALUES, List.of(), KIT);

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
                VALUES, List.of(), KIT);

        assertEquals(OverflowPolicy.Action.TAKE_INTO_FREE_SLOT, decision.action(),
                "Алмаз добирается до полной стопки своего же слота: выброс ради кучки, которая помещается "
                        + "частично, уничтожил бы лишний предмет");
    }

    @Test
    void decide_DropAndTake_KeepsEveryKitRuleInsideQuota() {
        List<Stack> inventory = inventory("bread:20;cobblestone:64*18;iron_ingot:1*17");
        List<Integer> before = new ArrayList<>();
        for (int index = 0; index < KIT.rules().size(); index++) {
            before.add(countOfRule(inventory, KIT.rules().get(index)));
        }

        OverflowPolicy.Decision decision = OverflowPolicy.decide(inventory, new Stack("diamond", 0, 1, 64),
                VALUES, List.of(), KIT);

        assertEquals(OverflowPolicy.Action.DROP, decision.action(),
                "Из полного инвентаря кучка алмаза берётся ценой лишнего: причина отказа — " + decision.reason());
        List<Stack> after = dropOnlyGivenAway(inventory, decision);
        List<KitRule> rules = KIT.rules();
        for (int index = 0; index < rules.size(); index++) {
            int left = countOfRule(after, rules.get(index));
            assertTrue(left >= Math.min(before.get(index), rules.get(index).keep()),
                    "После обмена по правилу набора '" + rules.get(index).match() + rules.get(index).category()
                            + "' осталось " + left + " предметов, а набор обязан был придержать минимум "
                            + Math.min(before.get(index), rules.get(index).keep()) + ": было " + before.get(index)
                            + ", квота " + rules.get(index).keep() + ". Отдали " + decision.count()
                            + " из слота " + decision.slot());
        }
    }

    @Test
    void decide_DropAndTake_RaisesTotalValue() {
        List<Stack> inventory = inventory("cobblestone:64*36");
        int before = totalValue(inventory);

        OverflowPolicy.Decision decision = OverflowPolicy.decide(inventory, new Stack("diamond", 0, 1, 64),
                VALUES, List.of(), KIT);
        List<Stack> after = dropOnlyGivenAway(inventory, decision);
        int incomingValue = VALUES.stackValue(new Stack("diamond", 0, 1, 64));
        int droppedValue = VALUES.valueOf(inventory.get(decision.slot()).item()) * decision.count();

        assertEquals(OverflowPolicy.Action.DROP, decision.action(),
                "Булыжник дешевле алмаза, поэтому политика отдаёт лишнее и берёт алмаз: причина "
                        + decision.reason());
        assertTrue(after.size() <= OverflowPolicy.INVENTORY_SLOTS,
                "После обмена слотов не больше " + OverflowPolicy.INVENTORY_SLOTS + ": стало "
                        + after.size() + ", лишний слот вышел бы за пределы инвентаря");
        assertTrue(totalValue(after) + incomingValue > before,
                "Сумма ценности того, что осталось, и подобранного выше прежней: было " + before
                        + ", осталось " + totalValue(after) + " и пришло " + incomingValue
                        + " (отдано ценностью " + droppedValue + "). Обмен, делающий духа беднее, — дефект");
    }

    @Test
    void decide_BaseKitQuota_DropsOnlySurplusOfItsSlots() {
        List<Stack> inventory = inventory("bread:16;cobblestone:64*35");

        OverflowPolicy.Decision decision = OverflowPolicy.decide(inventory, new Stack("diamond", 0, 1, 64),
                VALUES, List.of(), KIT);

        OverflowPolicy.Decision breadFirst = OverflowPolicy.decide(inventory,
                new Stack("diamond", 0, 1, 64), VALUES, List.of("bread"), KIT);

        assertEquals(OverflowPolicy.Action.DROP, decision.action(),
                "Квота набора не мешает отдать излишек: булыжник сверх 32 уходит в контейнер");
        assertEquals(0, Protected.of(List.of(), inventory, KIT, VALUES).giveAway(0),
                "Хлеб в пределах квоты 16 защищён: из его слота нечего отдавать, поэтому алмаз вытесняет "
                        + "булыжник, а не еду набора");
        assertEquals(OverflowPolicy.Action.DROP, breadFirst.action(),
                "Шаблон плана защищает хлеб, но булыжник остаётся излишком: набор отдаёт его, а не еду. "
                        + "Причина " + breadFirst.reason());
        assertEquals(35, decision.slot(),
                "Из равных стопок булыжника выбрасывается слот с наибольшим номером (35)");
        assertEquals(64, decision.count(),
                "Кирка и хлеб остаются на месте, а булыжник уходит стопкой целиком: в выбранном слоте "
                        + "все 64 предмета — излишек");
    }

    @Test
    void decide_SlotInsideKitQuota_IsNeverDropped() {
        List<Stack> inventory = inventory("bread:16;cobblestone:64*35");
        Protected protection = Protected.of(List.of(), inventory, KIT, VALUES);

        assertEquals(0, protection.giveAway(0),
                "Хлеб ровно по квоте 16 не имеет излишка: отдавать из этого слота нечего");
        assertTrue(protection.giveAway(1) > 0,
                "Булыжник сверх квоты 32 имеет излишек: его можно отдать, не трогая набор");
    }

    @Test
    void decide_PlanNeedsProtectedByPattern_AreNotDropped() {
        List<Stack> inventory = inventory("oak_planks:64*18;cobblestone:64*18");

        OverflowPolicy.Decision decision = OverflowPolicy.decide(inventory, new Stack("diamond", 0, 1, 64),
                VALUES, List.of("oak_planks"), KIT);

        assertEquals(OverflowPolicy.Action.DROP, decision.action(),
                "Доски, нужные плану (крафт), не выбрасываются: защита «нужного плану» сильнее дешевизны "
                        + "предмета");
        assertEquals(35, decision.slot(),
                "Выбрасывается булыжник с наибольшего номера, а не доски плана: доски дешевле, "
                        + "но они защищены шаблоном");
    }

    @Test
    void decide_SameInputs_GiveSameDecision() {
        List<Stack> inventory = List.of(
                new Stack("cobblestone", 0, 64, 64),
                new Stack("dirt", 0, 64, 64),
                new Stack("iron_ingot", 0, 1, 64));
        Stack incoming = new Stack("diamond", 0, 1, 64);

        OverflowPolicy.Decision first = OverflowPolicy.decide(inventory, incoming, VALUES, List.of(), KIT);
        OverflowPolicy.Decision second = OverflowPolicy.decide(inventory, incoming, VALUES, List.of(), KIT);

        assertEquals(first, second,
                "Одинаковый вход даёт одинаковое решение: политика работает на серверном тике, "
                        + "и разного выбора на одном и том же инвентаре быть не должно");
    }

    @Test
    void decide_AllProtected_ReasonNamesProtection() {
        List<Stack> inventory = inventory("iron_pickaxe:1*18;bread:16*18");

        OverflowPolicy.Decision decision = OverflowPolicy.decide(inventory, new Stack("cobblestone", 0, 1, 64),
                VALUES, List.of("iron_pickaxe", "bread"), KIT);

        assertEquals(OverflowPolicy.Action.REFUSE, decision.action(),
                "Когда всё защищено, кучка не берётся: базовый набор и нужное плану важнее находки");
        assertTrue(decision.reason().contains("инвентарь полон"),
                "Причина отказа начинается с «инвентарь полон»: коллектор показывает этот текст как итог "
                        + "collect_drops. Фактическая причина: " + decision.reason());
    }

    @Test
    void decide_RefuseReason_NamesBothStacks() {
        List<Stack> inventory = inventory("diamond:1*36");

        OverflowPolicy.Decision decision = OverflowPolicy.decide(inventory, stack("cobblestone:64"), VALUES,
                List.of(), KIT);

        assertEquals(OverflowPolicy.Action.REFUSE, decision.action(),
                "Дешёвая кучка не вытесняет алмаз: причина отказа должна объяснять, почему");
        assertTrue(decision.reason().contains("cobblestone") && decision.reason().contains("diamond"),
                "Причина отказа называет обе стопки — новую и самую дешёвую в инвентаре: по ней игрок "
                        + "понимает, что дух не стал брать мусор. Фактическая причина: " + decision.reason());
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

    private static List<Stack> dropOnlyGivenAway(List<Stack> inventory, OverflowPolicy.Decision decision) {
        List<Stack> after = new ArrayList<>(inventory);
        Stack slot = after.get(decision.slot());
        int left = slot.count() - decision.count();
        after.set(decision.slot(), left == 0 ? null : slot.withCount(left));
        return after;
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
            if (stack != null) {
                total += VALUES.stackValue(stack);
            }
        }
        return total;
    }

}
