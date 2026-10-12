package com.theminespirit.core.inventory;

import java.util.List;
import java.util.Objects;

public final class OverflowPolicy {
    public static final int INVENTORY_SLOTS = 36;

    private OverflowPolicy() {
    }

    public static Decision decide(List<Stack> inventory, Stack incoming, ValueTable values, List<String> planNeeds,
            BaseKit kit) {
        Objects.requireNonNull(inventory, "Инвентарь не может быть null: решение принимают по слотам инвентаря");
        Objects.requireNonNull(incoming, "Новая кучка не может быть null: без неё нечем пополнять инвентарь");
        Objects.requireNonNull(values, "Таблица ценностей не может быть null: "
                + "по ней решают, что дороже — новая кучка или то, что уже лежит");
        Objects.requireNonNull(planNeeds, "Список нужного плану не может быть null: "
                + "пустой список нужного задаётся пустым списком");
        Objects.requireNonNull(kit, "Базовый набор не может быть null: "
                + "без него дух считал бы излишком и последний хлеб");
        if (hasRoomFor(inventory, incoming)) {
            return new Decision(Action.TAKE_INTO_FREE_SLOT, -1, 0, null);
        }
        Protected protection = Protected.of(planNeeds, inventory, kit, values);
        int cheapest = cheapestDroppable(inventory, values, protection);
        if (cheapest < 0) {
            return new Decision(Action.REFUSE, -1, 0, "инвентарь полон: выбрасывать нечего, всё лежащее "
                    + "защищено базовым набором или нужно плану");
        }
        Stack slot = inventory.get(cheapest);
        int incomingValue = values.stackValue(incoming);
        int slotValue = values.stackValue(slot);
        if (incomingValue <= slotValue) {
            return new Decision(Action.REFUSE, -1, 0, "инвентарь полон: новая кучка '" + incoming.item() + "' × "
                    + incoming.count() + " ценностью " + incomingValue + " не дороже самой дешёвой стопки в слоте "
                    + cheapest + " ('" + slot.item() + "' × " + slot.count() + " ценностью " + slotValue + ")");
        }
        return new Decision(Action.DROP, cheapest, slot.count(), "в слоте " + cheapest + " весь '" + slot.item()
                + "' × " + slot.count() + " — лишнее сверх набора (ценностью " + slotValue + "): новая кучка дороже ("
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

    private static int cheapestDroppable(List<Stack> inventory, ValueTable values, Protected protection) {
        int found = -1;
        for (int slot = 0; slot < inventory.size(); slot++) {
            Stack stack = inventory.get(slot);
            if (stack == null || protection.giveAway(slot) != stack.count()) {
                continue;
            }
            if (found < 0 || cheaperThan(values, stack, inventory.get(found), slot, found)) {
                found = slot;
            }
        }
        return found;
    }

    private static boolean cheaperThan(ValueTable values, Stack candidate, Stack kept, int candidateSlot,
            int keptSlot) {
        int byStack = Integer.compare(values.stackValue(candidate), values.stackValue(kept));
        if (byStack != 0) {
            return byStack < 0;
        }
        int byUnit = Integer.compare(values.valueOf(candidate.item()), values.valueOf(kept.item()));
        if (byUnit != 0) {
            return byUnit < 0;
        }
        return candidateSlot > keptSlot;
    }

    public enum Action {
        TAKE_INTO_FREE_SLOT,
        DROP,
        REFUSE
    }

    public record Decision(Action action, int slot, int count, String reason) {
        public Decision {
            Objects.requireNonNull(action, "Действие политики не может быть null: "
                    + "без действия коллектор не знает, брать кучку или нет");
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
