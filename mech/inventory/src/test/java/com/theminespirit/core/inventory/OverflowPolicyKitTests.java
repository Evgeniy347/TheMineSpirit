package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OverflowPolicyKitTests {
    private static final ValueTable VALUES = ValueTable.load();
    private static final BaseKit KIT = BaseKit.load();

    @Test
    void decide_BaseKitQuota_DropsOnlySurplusOfItsSlots() {
        List<Stack> inventory = inventory("bread:16*35;torch:24");

        OverflowPolicy.Decision decision =
                OverflowPolicy.decide(inventory, new Stack("diamond", 0, 1, 64), VALUES, List.of(), KIT);

        assertEquals(
                OverflowPolicy.Action.DROP,
                decision.action(),
                "Квота набора не мешает отдать излишек: хлеб сверх 16 уходит стопкой целиком, иначе лишнее "
                        + "останется у духа. При смене decide проверь излишек при полном инвентаре");
        assertEquals(
                0,
                Protected.of(List.of(), inventory, KIT, VALUES).giveAway(0),
                "Хлеб в пределах квоты 16 защищён: из его слота нечего отдавать, иначе алмаз вытеснит "
                        + "еду набора. При смене decide проверь защиту квоты");
        assertEquals(
                34,
                decision.slot(),
                "Из равных стопок хлеба выбрасывается слот с наибольшим номером (34), иначе выбор слота "
                        + "непредсказуем. При смене decide проверь выбор слота");
        assertEquals(
                16,
                decision.count(),
                "Факелы в квоте 24 остаются на месте, а хлеб уходит стопкой целиком: в выбранном слоте "
                        + "все 16 предмета — излишек, иначе часть набора уйдёт. При смене decide проверь размер выброса");
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
                        + "иначе вместе с ним пропадёт весь склад. При смене decide проверь шалкер; причина "
                        + decision.reason());
    }

    @Test
    void decide_PartialSurplusSlot_IsSkippedForWholeSurplusSlot() {
        List<Stack> inventory = inventory("bread:20;bread:16*35");

        OverflowPolicy.Decision decision =
                OverflowPolicy.decide(inventory, new Stack("diamond", 0, 1, 64), VALUES, List.of(), KIT);

        assertEquals(
                OverflowPolicy.Action.DROP,
                decision.action(),
                "Инвентарь из хлеба всегда найдёт, чем погасить алмаз: 35 стопок целиком вне квоты еды, "
                        + "иначе кучка останется лежать. При смене decide проверь частичный излишек");
        assertEquals(
                35,
                decision.slot(),
                "Слот с частичным излишком (20 хлеба при квоте 16) не выбирается: иначе выбросив из него 4, "
                        + "дух оставляет слот занятым и алмазу некуда деться. При смене decide проверь выбор слота");
        assertEquals(
                16,
                decision.count(),
                "Выбранная стопка уходит целиком: в ней 16 хлебов, все за пределами квоты еды (16), "
                        + "иначе слот останется занятым. При смене decide проверь размер выброса");
    }

    @Test
    void decide_SlotInsideKitQuota_IsNeverDropped() {
        List<Stack> inventory = inventory("bread:16*35;torch:24");
        Protected protection = Protected.of(List.of(), inventory, KIT, VALUES);

        assertEquals(
                0,
                protection.giveAway(35),
                "24 факела ровно по квоте 24 не имеют излишка: иначе шахта останется без света. "
                        + "При смене giveAway проверь слот факелов в полном инвентаре");
        assertTrue(
                protection.giveAway(0) == 0,
                "Хлеб в слоте 0 получил всю квоту 16: из его слота нечего отдавать, иначе еда уйдёт на склад. "
                        + "При смене giveAway проверь слот 0");
        assertTrue(
                protection.giveAway(34) > 0,
                "Хлеб в слоте 34 остался без квоты: его можно отдать, не трогая набор, иначе лишнее "
                        + "забьёт инвентарь. При смене giveAway проверь слот 34");
    }

    @Test
    void decide_PlanNeedsProtectedByPattern_AreNotDropped() {
        List<Stack> inventory = inventory("oak_planks:64*18;cobblestone:64*18");

        OverflowPolicy.Decision decision =
                OverflowPolicy.decide(inventory, new Stack("diamond", 0, 1, 64), VALUES, List.of("oak_planks"), KIT);

        assertEquals(
                OverflowPolicy.Action.DROP,
                decision.action(),
                "Доски, нужные плану (крафт), не выбрасываются: иначе защита нужного плану слабее "
                        + "дешевизны предмета. При смене decide проверь защиту плана");
        assertEquals(
                35,
                decision.slot(),
                "Выбрасывается булыжник с наибольшего номера, а не доски плана: иначе крафт останется "
                        + "без материала. При смене decide проверь выбор слота");
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
                "Из полного инвентаря кучка алмаза берётся ценой лишнего, иначе кучка останется лежать: "
                        + "причина отказа — " + decision.reason() + ". При смене decide проверь обмен");
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
                            + " из слота " + decision.slot() + ", иначе набор разъединится. "
                            + "При смене decide проверь квоту правила");
        }
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
}
