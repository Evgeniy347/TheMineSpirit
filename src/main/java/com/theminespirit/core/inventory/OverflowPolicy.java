package com.theminespirit.core.inventory;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class OverflowPolicy {
    public static final int INVENTORY_SLOTS = 36;

    private OverflowPolicy() {
    }

    public static Decision decide(List<Stack> inventory, Stack incoming, ValueTable values, Protected protection) {
        Objects.requireNonNull(inventory, "Инвентарь не может быть null: решение принимают по слотам инвентаря");
        Objects.requireNonNull(incoming, "Новая кучка не может быть null: без неё нечем пополнять инвентарь");
        Objects.requireNonNull(values, "Таблица ценностей не может быть null: "
                + "по ней решают, что дороже — новая кучка или то, что уже лежит");
        Objects.requireNonNull(protection, "Защита не может быть null: без неё дух выбрасывал бы базовый набор");
        if (hasRoomFor(inventory, incoming)) {
            return new Decision(Action.TAKE_INTO_FREE_SLOT, -1, 0, null);
        }
        int cheapest = cheapestDroppable(inventory, values, protection);
        if (cheapest < 0) {
            return new Decision(Action.REFUSE, -1, 0, "инвентарь полон: все " + INVENTORY_SLOTS
                    + " слотов заняты защищёнными предметами, выбрасывать нечего");
        }
        Stack slot = inventory.get(cheapest);
        int incomingValue = values.stackValue(incoming);
        int slotValue = values.stackValue(slot);
        if (incomingValue <= slotValue) {
            return new Decision(Action.REFUSE, -1, 0, "инвентарь полон: новая кучка '" + incoming.item() + "' × "
                    + incoming.count() + " ценностью " + incomingValue + " не дороже самой дешёвой стопки '"
                    + slot.item() + "' ценностью " + slotValue + " в слоте " + cheapest);
        }
        return new Decision(Action.DROP, cheapest, slot.count(), "в слоте " + cheapest + " лежит '" + slot.item()
                + "' × " + slot.count() + " ценностью " + slotValue + ", новая кучка дороже (" + incomingValue
                + ")");
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
        Comparator<Stack> cheapest = Comparator
                .comparingInt(values::stackValue)
                .thenComparingInt(stack -> values.valueOf(stack.item()));
        int found = -1;
        for (int slot = 0; slot < inventory.size(); slot++) {
            Stack stack = inventory.get(slot);
            if (stack == null || protection.shieldsSlot(slot)) {
                continue;
            }
            if (found < 0 || cheapest.compare(stack, inventory.get(found)) <= 0) {
                found = slot;
            }
        }
        return found;
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
