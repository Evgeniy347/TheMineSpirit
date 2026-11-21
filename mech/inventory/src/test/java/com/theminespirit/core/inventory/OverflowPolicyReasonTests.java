package com.theminespirit.core.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OverflowPolicyReasonTests {
    private static final ValueTable VALUES = ValueTable.load();
    private static final BaseKit KIT = BaseKit.load();

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
                "Одинаковый вход даёт одинаковое решение: иначе выбор дропа зависит от порядка вызовов "
                        + "и серверный тик получает разный результат. При смене decide проверь детерминизм");
    }

    @Test
    void decide_AllProtected_ReasonNamesProtection() {
        List<Stack> inventory = inventory("iron_pickaxe:1*18;bread:16*18");

        OverflowPolicy.Decision decision = OverflowPolicy.decide(
                inventory, new Stack("cobblestone", 0, 1, 64), VALUES, List.of("iron_pickaxe", "bread"), KIT);

        assertEquals(
                OverflowPolicy.Action.REFUSE,
                decision.action(),
                "Когда всё защищено, кучка не берётся: иначе базовый набор и нужное плану окажутся "
                        + "слабее находки. При смене decide проверь полную защиту");
        assertTrue(
                decision.reason().contains("инвентарь полон"),
                "Причина отказа начинается с «инвентарь полон», иначе коллектор не покажет причину: "
                        + "фактическая причина: " + decision.reason()
                        + ". При смене текста причины проверь начало фразы");
    }

    @Test
    void decide_RefuseReason_NamesBothStacks() {
        List<Stack> inventory = inventory("diamond:1*36");

        OverflowPolicy.Decision decision =
                OverflowPolicy.decide(inventory, stack("cobblestone:64"), VALUES, List.of(), KIT);

        assertEquals(
                OverflowPolicy.Action.REFUSE,
                decision.action(),
                "Дешёвая кучка не вытесняет алмаз: иначе политика выбросит ценное ради мусора и дух "
                        + "обеднеет. При смене decide проверь отказ при дешёвой кучке");
        assertTrue(
                decision.reason().contains("cobblestone") && decision.reason().contains("diamond"),
                "Причина отказа называет обе стопки — новую и самую дешёвую в инвентаре, иначе игрок "
                        + "не поймёт отказ; фактическая причина: " + decision.reason()
                        + ". При смене текста причины проверь обе стопки");
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
                        + "против 500 за шалкер), иначе квоты и склад будут потеряны; причина "
                        + decision.reason() + ". При смене decide проверь квоты и шалкеры");
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
