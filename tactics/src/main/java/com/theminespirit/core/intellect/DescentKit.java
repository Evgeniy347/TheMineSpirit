package com.theminespirit.core.intellect;

import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.inventory.ValueTable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DescentKit {
    private DescentKit() {}

    public static List<String> missing(List<OutsideRules.Need> needs, List<Stack> inventory, ValueTable values) {
        Objects.requireNonNull(needs, "Набор шахты не может быть null: пустой набор задают пустым списком");
        Objects.requireNonNull(inventory, "Инвентарь не может быть null: по нему считают нехватку набора");
        Objects.requireNonNull(values, "Таблица ценностей не может быть null: по ней нужда находит категорию");
        List<String> missing = new ArrayList<>();
        for (OutsideRules.Need need : needs) {
            int have = 0;
            for (Stack stack : inventory) {
                if (stack != null && need.covers(stack.item(), values)) {
                    have += stack.count();
                }
            }
            if (have < need.count()) {
                missing.add(need.describe() + ": " + have + " из " + need.count());
            }
        }
        return List.copyOf(missing);
    }
}
