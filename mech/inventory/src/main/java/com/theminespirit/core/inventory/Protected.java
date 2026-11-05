package com.theminespirit.core.inventory;

import com.theminespirit.core.observation.IdFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class Protected {
    private final List<String> patterns;
    private final @Nullable IdFilter filter;
    private final List<Stack> inventory;
    private final int[] giveAway;

    private Protected(List<String> patterns, List<Stack> inventory, int[] giveAway) {
        this.patterns = List.copyOf(patterns);
        this.filter = this.patterns.isEmpty() ? null : IdFilter.of(this.patterns);
        this.inventory = inventory;
        this.giveAway = giveAway;
    }

    public static Protected of(List<String> patterns, List<Stack> inventory, BaseKit kit, ValueTable values) {
        Objects.requireNonNull(
                patterns, "Список защищённых шаблонов не может быть null: " + "пустая защита задаётся пустым списком");
        Objects.requireNonNull(inventory, "Инвентарь не может быть null: по его слотам считают излишек набора");
        Objects.requireNonNull(kit, "Базовый набор не может быть null: без него квоты набора не проверить");
        Objects.requireNonNull(values, "Таблица ценностей не может быть null: " + "по ней набор выбирает лучшую кирку");
        int[] giveAway = new int[inventory.size()];
        for (Slice slice : kit.surplus(inventory, values)) {
            giveAway[slice.slot()] = slice.count();
        }
        List<String> shields = new ArrayList<>(patterns);
        shields.addAll(kit.neverGiveAway());
        return new Protected(shields, Collections.unmodifiableList(new ArrayList<>(inventory)), giveAway);
    }

    public int giveAway(int slot) {
        if (slot < 0 || slot >= inventory.size()) {
            return 0;
        }
        if (inventory.get(slot) == null) {
            return 0;
        }
        if (filter != null && filter.matches(inventory.get(slot).item())) {
            return 0;
        }
        return giveAway[slot];
    }
}
