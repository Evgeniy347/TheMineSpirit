package com.theminespirit.core.inventory;

import com.theminespirit.core.observation.IdFilter;
import java.util.List;
import java.util.Objects;

public final class Protected {
    private static final Protected NOTHING = new Protected(List.of(), null, List.of(), null);

    private final List<String> patterns;
    private final IdFilter filter;
    private final BaseKit kit;
    private final List<Stack> inventory;
    private final int[] giveAway;

    private Protected(List<String> patterns, BaseKit kit, List<Stack> inventory, int[] giveAway) {
        this.patterns = List.copyOf(patterns);
        this.filter = this.patterns.isEmpty() ? null : IdFilter.of(this.patterns);
        this.kit = kit;
        this.inventory = inventory;
        this.giveAway = giveAway;
    }

    public static Protected nothing() {
        return NOTHING;
    }

    public static Protected of(List<String> patterns, List<Stack> inventory) {
        Objects.requireNonNull(patterns, "Список защищённых шаблонов не может быть null: "
                + "пустая защита задаётся пустым списком");
        Objects.requireNonNull(inventory, "Инвентарь не может быть null: по слотам решают, что защищено");
        if (patterns.isEmpty()) {
            return NOTHING;
        }
        return new Protected(patterns, null, List.copyOf(inventory), new int[inventory.size()]);
    }

    public static Protected withBaseKit(List<String> patterns, BaseKit kit, List<Stack> inventory,
            ValueTable values) {
        Objects.requireNonNull(patterns, "Список защищённых шаблонов не может быть null: "
                + "пустая защита задаётся пустым списком");
        Objects.requireNonNull(kit, "Базовый набор не может быть null: без него квоты набора не проверить");
        Objects.requireNonNull(inventory, "Инвентарь не может быть null: по слотам считают квоты набора");
        Objects.requireNonNull(values, "Таблица ценностей не может быть null: "
                + "по ней набор выбирает лучшую кирку");
        List<Slice> surplus = kit.surplus(inventory, values);
        int[] giveAway = new int[inventory.size()];
        for (Slice slice : surplus) {
            giveAway[slice.slot()] = slice.count();
        }
        return new Protected(patterns, kit, List.copyOf(inventory), giveAway);
    }

    public boolean shields(String item) {
        Objects.requireNonNull(item, "Предмет не назван: защита проверяется по id предмета");
        return filter != null && filter.matches(item);
    }

    public boolean shieldsSlot(int slot) {
        if (filter == null && kit == null) {
            return false;
        }
        if (slot < 0 || slot >= inventory.size()) {
            return true;
        }
        Stack stack = inventory.get(slot);
        if (stack == null) {
            return true;
        }
        if (filter != null && filter.matches(stack.item())) {
            return true;
        }
        return kit != null && giveAway[slot] == 0;
    }

    public List<String> patterns() {
        return patterns;
    }
}
