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
                        + decision.reason());
        if (decision.action() == OverflowPolicy.Action.DROP) {
            assertEquals(
                    expectedSlot,
                    decision.slot(),
                    "Ситуация '" + situation + "': выбросить должен самый дешёвый незащищённый слот " + expectedSlot);
            assertEquals(
                    expectedCount,
                    decision.count(),
                    "Ситуация '" + situation + "': выбрасывается слот целиком и только когда весь он "
                            + "излишек — частичный выброс оставляет слот занятым и кучке некуда деться");
            assertTrue(
                    expectedCount == inventory.get(decision.slot()).count(),
                    "Ситуация '" + situation + "': выбранный слот выбрасывается целиком (" + expectedCount
                            + "), в нём лежало " + inventory.get(decision.slot()));
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
                "Алмаз дороже любой из стопок, поэтому обмен возможен: причина " + decision.reason());
        assertEquals(
                "iron_ingot",
                inventory.get(decision.slot()).item(),
                "При огромном запасе железа на базе его ценность падает ниже брёвен, и выбрасывается "
                        + "именно железо, а редкое дерево остаётся у духа. Решение: " + decision.reason());
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
                "Излишек железа вне квоты набора уходит: причина " + decision.reason());
        assertTrue(
                !"bread".equals(inventory.get(decision.slot()).item()),
                "Норма еды не выбрасывается даже когда её на базе много: хлеб защищён базовым "
                        + "набором, а складская цена не может отнять квоту");
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
                "Алмазов на базе много, дерева нет: дешёвый по складу алмаз не вытесняет редкое " + "бревно, причина "
                        + decision.reason());
    }

    @Test
    void decide_InventoryHasFreeSlot_TakesWithoutDropping() {
        List<Stack> inventory = List.of(new Stack("cobblestone", 0, 1, 64), new Stack("iron_ingot", 0, 5, 64));

        OverflowPolicy.Decision decision =
                OverflowPolicy.decide(inventory, new Stack("diamond", 0, 1, 64), VALUES, List.of(), KIT);

        assertEquals(
                OverflowPolicy.Action.TAKE_INTO_FREE_SLOT,
                decision.action(),
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

        OverflowPolicy.Decision decision =
                OverflowPolicy.decide(inventory, new Stack("diamond", 0, 1, 64), VALUES, List.of(), KIT);

        assertEquals(
                OverflowPolicy.Action.TAKE_INTO_FREE_SLOT,
                decision.action(),
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

        OverflowPolicy.Decision decision =
                OverflowPolicy.decide(inventory, new Stack("diamond", 0, 1, 64), VALUES, List.of(), KIT);

        assertEquals(
                OverflowPolicy.Action.DROP,
                decision.action(),
                "Из полного инвентаря кучка алмаза берётся ценой лишнего: причина отказа — " + decision.reason());
        List<Stack> after = dropOnlyGivenAway(inventory, decision);
        List<KitRule> rules = KIT.rules();
        for (int index = 0; index < rules.size(); index++) {
            int left = countOfRule(after, rules.get(index));
            assertTrue(
                    left >= Math.min(before.get(index), rules.get(index).keep()),
                    "После обмена по правилу набора '" + rules.get(index).match()
                            + rules.get(index).category()
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

        OverflowPolicy.Decision decision =
                OverflowPolicy.decide(inventory, new Stack("diamond", 0, 1, 64), VALUES, List.of(), KIT);
        List<Stack> after = dropOnlyGivenAway(inventory, decision);
        int incomingValue = VALUES.stackValue(new Stack("diamond", 0, 1, 64));
        int droppedValue = VALUES.valueOf(inventory.get(decision.slot()).item()) * decision.count();

        assertEquals(
                OverflowPolicy.Action.DROP,
                decision.action(),
                "Булыжник дешевле алмаза, поэтому политика отдаёт лишнее и берёт алмаз: причина " + decision.reason());
        assertTrue(
                after.size() <= OverflowPolicy.INVENTORY_SLOTS,
                "После обмена слотов не больше " + OverflowPolicy.INVENTORY_SLOTS + ": стало " + after.size()
                        + ", лишний слот вышел бы за пределы инвентаря");
        assertTrue(
                totalValue(after) + incomingValue > before,
                "Сумма ценности того, что осталось, и подобранного выше прежней: было " + before
                        + ", осталось " + totalValue(after) + " и пришло " + incomingValue
                        + " (отдано ценностью " + droppedValue + "). Обмен, делающий духа беднее, — дефект");
    }

    @Test
    void decide_BaseKitQuota_DropsOnlySurplusOfItsSlots() {
        List<Stack> inventory = inventory("bread:16*35;torch:24");

        OverflowPolicy.Decision decision =
                OverflowPolicy.decide(inventory, new Stack("diamond", 0, 1, 64), VALUES, List.of(), KIT);

        assertEquals(
                OverflowPolicy.Action.DROP,
                decision.action(),
                "Квота набора не мешает отдать излишек: хлеб сверх 16 уходит стопкой целиком");
        assertEquals(
                0,
                Protected.of(List.of(), inventory, KIT, VALUES).giveAway(0),
                "Хлеб в пределах квоты 16 защищён: из его слота нечего отдавать, поэтому алмаз вытесняет "
                        + "стопку хлеба сверх квоты, а не еду набора");
        assertEquals(34, decision.slot(), "Из равных стопок хлеба выбрасывается слот с наибольшим номером (34)");
        assertEquals(
                16,
                decision.count(),
                "Факелы в квоте 24 остаются на месте, а хлеб уходит стопкой целиком: в выбранном слоте "
                        + "все 16 предмета — излишек");
    }

    @Test
    void decide_ShulkerAsCheapestSlot_IsNeverDropped() {
        ValueTable cheapShulker = ValueTable.parse("""
                {"default": 1,
                 "rules": [{"match": ["white_shulker_box", "shulker_box"], "value": 0},
                           {"match": ["diamond"], "value": 10},
                           {"match": ["netherite_block"], "value": 1000}],
                 "categories": [{"name": "misc", "match": ["*"]}]}
                """, "тест: шалкер дешевле всего");
        List<Stack> inventory = inventory("white_shulker_box:1;diamond:1*35");

        OverflowPolicy.Decision decision =
                OverflowPolicy.decide(inventory, stack("netherite_block:1"), cheapShulker, List.of("diamond"), KIT);

        assertEquals(
                OverflowPolicy.Action.REFUSE,
                decision.action(),
                "Шалкер-бокс не выбрасывается никогда, даже если он самый дешёвый предмет в инвентаре: "
                        + "внутри может лежать склад, а девять слотов шалкера дороже любой ценности по "
                        + "шкале. Причина " + decision.reason());
    }

    @Test
    void decide_PartialSurplusSlot_IsSkippedForWholeSurplusSlot() {
        List<Stack> inventory = inventory("bread:20;bread:16*35");

        OverflowPolicy.Decision decision =
                OverflowPolicy.decide(inventory, new Stack("diamond", 0, 1, 64), VALUES, List.of(), KIT);

        assertEquals(
                OverflowPolicy.Action.DROP,
                decision.action(),
                "Инвентарь из хлеба всегда найдёт, чем погасить алмаз: 35 стопок целиком вне квоты еды");
        assertEquals(
                35,
                decision.slot(),
                "Слот с частичным излишком (20 хлеба при квоте 16) не выбирается: выбросив из него 4, "
                        + "дух оставляет слот занятым и алмазу некуда деться. Выбирается стопка, "
                        + "которая целиком лишняя");
        assertEquals(
                16,
                decision.count(),
                "Выбранная стопка уходит целиком: в ней 16 хлебов, все за пределами квоты еды (16)");
    }

    @Test
    void decide_SlotInsideKitQuota_IsNeverDropped() {
        List<Stack> inventory = inventory("bread:16*35;torch:24");
        Protected protection = Protected.of(List.of(), inventory, KIT, VALUES);

        assertEquals(
                0,
                protection.giveAway(35),
                "24 факела ровно по квоте 24 не имеют излишка: отдавать из этого слота нечего");
        assertTrue(protection.giveAway(0) == 0, "Хлеб в слоте 0 получил всю квоту 16: из его слота нечего отдавать");
        assertTrue(protection.giveAway(34) > 0, "Хлеб в слоте 34 остался без квоты: его можно отдать, не трогая набор");
    }

    @Test
    void decide_PlanNeedsProtectedByPattern_AreNotDropped() {
        List<Stack> inventory = inventory("oak_planks:64*18;cobblestone:64*18");

        OverflowPolicy.Decision decision =
                OverflowPolicy.decide(inventory, new Stack("diamond", 0, 1, 64), VALUES, List.of("oak_planks"), KIT);

        assertEquals(
                OverflowPolicy.Action.DROP,
                decision.action(),
                "Доски, нужные плану (крафт), не выбрасываются: защита «нужного плану» сильнее дешевизны "
                        + "предмета");
        assertEquals(
                35,
                decision.slot(),
                "Выбрасывается булыжник с наибольшего номера, а не доски плана: доски дешевле, "
                        + "но они защищены шаблоном");
    }

    @Test
    void decide_SameInputs_GiveSameDecision() {
        List<Stack> inventory = List.of(
                new Stack("cobblestone", 0, 64, 64), new Stack("dirt", 0, 64, 64), new Stack("iron_ingot", 0, 1, 64));
        Stack incoming = new Stack("diamond", 0, 1, 64);

        OverflowPolicy.Decision first = OverflowPolicy.decide(inventory, incoming, VALUES, List.of(), KIT);
        OverflowPolicy.Decision second = OverflowPolicy.decide(inventory, incoming, VALUES, List.of(), KIT);

        assertEquals(
                first,
                second,
                "Одинаковый вход даёт одинаковое решение: политика работает на серверном тике, "
                        + "и разного выбора на одном и том же инвентаре быть не должно");
    }

    @Test
    void decide_AllProtected_ReasonNamesProtection() {
        List<Stack> inventory = inventory("iron_pickaxe:1*18;bread:16*18");

        OverflowPolicy.Decision decision = OverflowPolicy.decide(
                inventory, new Stack("cobblestone", 0, 1, 64), VALUES, List.of("iron_pickaxe", "bread"), KIT);

        assertEquals(
                OverflowPolicy.Action.REFUSE,
                decision.action(),
                "Когда всё защищено, кучка не берётся: базовый набор и нужное плану важнее находки");
        assertTrue(
                decision.reason().contains("инвентарь полон"),
                "Причина отказа начинается с «инвентарь полон»: коллектор показывает этот текст как итог "
                        + "collect_drops. Фактическая причина: " + decision.reason());
    }

    @Test
    void decide_RefuseReason_NamesBothStacks() {
        List<Stack> inventory = inventory("diamond:1*36");

        OverflowPolicy.Decision decision =
                OverflowPolicy.decide(inventory, stack("cobblestone:64"), VALUES, List.of(), KIT);

        assertEquals(
                OverflowPolicy.Action.REFUSE,
                decision.action(),
                "Дешёвая кучка не вытесняет алмаз: причина отказа должна объяснять, почему");
        assertTrue(
                decision.reason().contains("cobblestone") && decision.reason().contains("diamond"),
                "Причина отказа называет обе стопки — новую и самую дешёвую в инвентаре: по ней игрок "
                        + "понимает, что дух не стал брать мусор. Фактическая причина: " + decision.reason());
    }

    @Test
    void decide_KitQuotasAndShulkersWithValuableHeap_RefusesWithoutDropping() {
        List<Stack> inventory = new ArrayList<>();
        inventory.add(new Stack("iron_sword", 0, 1, 1));
        inventory.add(new Stack("bow", 0, 1, 1));
        inventory.add(new Stack("arrow", 0, 16, 64));
        inventory.add(new Stack("bread", 0, 16, 64));
        inventory.add(new Stack("torch", 0, 24, 64));
        for (int slot = 5; slot < 36; slot++) {
            inventory.add(new Stack("shulker_box", 0, 1, 1));
        }

        OverflowPolicy.Decision decision =
                OverflowPolicy.decide(inventory, new Stack("diamond", 0, 2, 64), VALUES, List.of(), KIT);

        assertEquals(
                OverflowPolicy.Action.REFUSE,
                decision.action(),
                "Инвентарь из квот постоянной части и шалкеров не отдаёт ничего даже за два алмаза (1000 "
                        + "против 500 за шалкер): квоты защищены набором, шалкеры — never_give_away. Причина "
                        + decision.reason());
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
