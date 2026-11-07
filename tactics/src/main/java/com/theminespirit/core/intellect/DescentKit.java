package com.theminespirit.core.intellect;

import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.inventory.ValueTable;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.script.CatalogLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

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

    public static List<ScriptCall> supply(
            List<OutsideRules.Need> needs, List<Stack> inventory, Map<String, Integer> stock, ValueTable values) {
        Objects.requireNonNull(needs, "Набор шахты не может быть null: пустой набор задают пустым списком");
        Objects.requireNonNull(inventory, "Инвентарь не может быть null: по нему считают нехватку набора");
        Objects.requireNonNull(stock, "Склад не может быть null: пустой склад задают пустой картой");
        Objects.requireNonNull(values, "Таблица ценностей не может быть null: по ней нужда находит категорию");
        if (needs.isEmpty() || stock.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> left = new TreeMap<>(stock);
        List<ScriptCall> steps = new ArrayList<>();
        for (OutsideRules.Need need : needs) {
            int want = need.count() - have(need, inventory, values);
            for (Map.Entry<String, Integer> item : left.entrySet()) {
                if (want <= 0) {
                    break;
                }
                if (item.getValue() <= 0 || !need.covers(item.getKey(), values)) {
                    continue;
                }
                int take = Math.min(want, item.getValue());
                steps.add(new ScriptCall(
                        "withdraw",
                        Map.of(
                                "container",
                                "warehouse",
                                "item",
                                item.getKey(),
                                "count",
                                Math.min(take, CatalogLimits.COUNT_MAX),
                                "radius",
                                CatalogLimits.CONTAINER_RADIUS_DEFAULT)));
                item.setValue(item.getValue() - take);
                want -= take;
            }
        }
        return List.copyOf(steps);
    }

    private static int have(OutsideRules.Need need, List<Stack> inventory, ValueTable values) {
        int have = 0;
        for (Stack stack : inventory) {
            if (stack != null && need.covers(stack.item(), values)) {
                have += stack.count();
            }
        }
        return have;
    }
}
