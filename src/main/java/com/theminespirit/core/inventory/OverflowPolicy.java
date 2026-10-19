package com.theminespirit.core.inventory;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class OverflowPolicy {
    public static final int INVENTORY_SLOTS = 36;

    private OverflowPolicy() {}

    public static Decision decide(
            List<Stack> inventory, Stack incoming, ValueTable values, List<String> planNeeds, BaseKit kit) {
        return decide(inventory, incoming, values, planNeeds, kit, Map.of(), OverflowTuning.defaults());
    }

    public static Decision decide(
            List<Stack> inventory,
            Stack incoming,
            ValueTable values,
            List<String> planNeeds,
            BaseKit kit,
            Map<String, Integer> baseStock,
            OverflowTuning tuning) {
        Objects.requireNonNull(inventory, "Инвентарь не может быть null: решение принимают по слотам инвентаря");
        Objects.requireNonNull(incoming, "Новая кучка не может быть null: без неё нечем пополнять инвентарь");
        Objects.requireNonNull(
                values,
                "Таблица ценностей не может быть null: "
                        + "по ней решают, что дороже — новая кучка или то, что уже лежит");
        Objects.requireNonNull(
                planNeeds,
                "Список нужного плану не может быть null: " + "пустой список нужного задаётся пустым списком");
        Objects.requireNonNull(
                kit, "Базовый набор не может быть null: " + "без него дух считал бы излишком и последний хлеб");
        Objects.requireNonNull(baseStock, "Запас базы не может быть null: пустой запас задают пустой картой");
        Objects.requireNonNull(tuning, "Таблица переполнения не может быть null: без неё запас не влияет на цену");
        if (hasRoomFor(inventory, incoming)) {
            return new Decision(Action.TAKE_INTO_FREE_SLOT, -1, 0, null);
        }
        Protected protection = Protected.of(planNeeds, inventory, kit, values);
        int cheapest = cheapestDroppable(inventory, values, protection, baseStock, tuning);
        if (cheapest < 0) {
            return new Decision(
                    Action.REFUSE,
                    -1,
                    0,
                    "инвентарь полон: выбрасывать нечего, всё лежащее " + "защищено базовым набором или нужно плану");
        }
        Stack slot = inventory.get(cheapest);
        int incomingValue =
                tuning.effectiveUnitValue(values.valueOf(incoming.item()), stockOf(baseStock, incoming.item()))
                        * incoming.count();
        int slotValue =
                tuning.effectiveUnitValue(values.valueOf(slot.item()), stockOf(baseStock, slot.item())) * slot.count();
        if (incomingValue <= slotValue) {
            return new Decision(
                    Action.REFUSE,
                    -1,
                    0,
                    "инвентарь полон: новая кучка '" + incoming.item() + "' × "
                            + incoming.count() + " ценностью " + incomingValue
                            + " не дороже самой дешёвой стопки в слоте "
                            + cheapest + " ('" + slot.item() + "' × " + slot.count() + " ценностью " + slotValue + ")");
        }
        return new Decision(
                Action.DROP,
                cheapest,
                slot.count(),
                "в слоте " + cheapest + " весь '" + slot.item()
                        + "' × " + slot.count() + " — лишнее сверх набора (ценностью " + slotValue
                        + "): новая кучка дороже ("
                        + incomingValue + ") и занимает освободившееся место");
    }

    private static boolean hasRoomFor(List<Stack> inventory, Stack incoming) {
        if (inventory.size() < INVENTORY_SLOTS || inventory.contains(null)) {
            return true;
        }
        for (Stack stack : inventory) {
            if (stack != null && stack.sameKindAs(incoming) && stack.freeSpace() > 0) {
                return true;
            }
        }
        return false;
    }

    private static int cheapestDroppable(
            List<Stack> inventory,
            ValueTable values,
            Protected protection,
            Map<String, Integer> baseStock,
            OverflowTuning tuning) {
        int found = -1;
        for (int slot = 0; slot < inventory.size(); slot++) {
            Stack stack = inventory.get(slot);
            if (stack == null || protection.giveAway(slot) != stack.count()) {
                continue;
            }
            if (found < 0 || cheaperThan(values, stack, inventory.get(found), slot, found, baseStock, tuning)) {
                found = slot;
            }
        }
        return found;
    }

    private static boolean cheaperThan(
            ValueTable values,
            Stack candidate,
            Stack kept,
            int candidateSlot,
            int keptSlot,
            Map<String, Integer> baseStock,
            OverflowTuning tuning) {
        int byStack = Integer.compare(
                stackValue(values, candidate, baseStock, tuning), stackValue(values, kept, baseStock, tuning));
        if (byStack != 0) {
            return byStack < 0;
        }
        int byUnit = Integer.compare(values.valueOf(candidate.item()), values.valueOf(kept.item()));
        if (byUnit != 0) {
            return byUnit < 0;
        }
        return candidateSlot > keptSlot;
    }

    private static int stackValue(
            ValueTable values, Stack stack, Map<String, Integer> baseStock, OverflowTuning tuning) {
        return tuning.effectiveUnitValue(values.valueOf(stack.item()), stockOf(baseStock, stack.item()))
                * stack.count();
    }

    private static int stockOf(Map<String, Integer> baseStock, String item) {
        Integer full = baseStock.get(item);
        if (full != null) {
            return full;
        }
        String prefix = "minecraft:";
        return item.startsWith(prefix) ? baseStock.getOrDefault(item.substring(prefix.length()), 0) : 0;
    }

    public enum Action {
        TAKE_INTO_FREE_SLOT,
        DROP,
        REFUSE
    }

    public record Decision(Action action, int slot, int count, String reason) {
        public Decision {
            Objects.requireNonNull(
                    action,
                    "Действие политики не может быть null: " + "без действия коллектор не знает, брать кучку или нет");
            if (action == Action.DROP && (slot < 0 || count <= 0)) {
                throw new IllegalArgumentException("Действие DROP выбрасывает слот " + slot + " (" + count
                        + " предметов): выбрасывать может только существующий непустой слот");
            }
            if (action == Action.REFUSE && (reason == null || reason.isBlank())) {
                throw new IllegalArgumentException("Отказ без причины: по причине дух сообщает, "
                        + "почему инвентарь полон, и ради неё политика существует");
            }
        }
    }
}
