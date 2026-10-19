package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.tactics.EnemyProfile;
import com.theminespirit.core.tactics.InvalidFieldException;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record DefenseProfile(
        Map<String, TargetClass> classes,
        TargetClass fallback,
        Map<DefenderRole, List<TargetClass>> priority,
        Map<DefenderRole, List<TargetClass>> priorityWithoutArcher) {
    public static final String DEFAULT_KEY = "default";
    public static final String RESOURCE = "data/theminespirit/tactics/priorities.json";

    public DefenseProfile {
        if (classes == null) {
            throw new IllegalArgumentException(
                    "classes не может быть null: без явной карты типов каждый враг упадёт в молчаливый fallback");
        }
        if (classes.isEmpty()) {
            throw new InvalidFieldException(
                    "DefenseProfile.classes",
                    "DefenseProfile.classes пуста: таблица классов целей не может быть пустой");
        }
        if (fallback == null) {
            throw new IllegalArgumentException(
                    "fallback не может быть null: неизвестный тип обязан получить реальный класс");
        }
        if (priority == null) {
            throw new IllegalArgumentException(
                    "priority не может быть null: без таблицы приоритетов цель выбирается случайно");
        }
        if (priorityWithoutArcher == null) {
            throw new IllegalArgumentException(
                    "priorityWithoutArcher не может быть null: мечник без лучника обязан знать свой порядок целей");
        }
        classes = Map.copyOf(classes);
        priority = checkedOrders("DefenseProfile.priority", priority, true);
        priorityWithoutArcher = checkedOrders("DefenseProfile.priorityWithoutArcher", priorityWithoutArcher, false);
    }

    public static DefenseProfile readFromClasspath() {
        return DefenseProfileParser.fromJson(JsonResource.readClasspath(RESOURCE));
    }

    public static DefenseProfile loadDefault() {
        return DataTables.INSTANCE.priorities().current();
    }

    public TargetClass classify(String type) {
        if (type == null) {
            throw new IllegalArgumentException(
                    "type не может быть null: класс цели считается по типу врага, без него только fallback");
        }
        return classes.getOrDefault(type, fallback);
    }

    public TargetClass classify(String type, EnemyProfile profile) {
        if (type == null) {
            throw new IllegalArgumentException(
                    "type не может быть null: класс цели считается по типу врага, без него только fallback");
        }
        TargetClass known = classes.get(type);
        if (known != null) {
            return known;
        }
        if (profile == null) {
            return fallback;
        }
        if (profile.explosive()) {
            return TargetClass.CREEPER;
        }
        if (profile.flies()) {
            return TargetClass.FLYER;
        }
        if (profile.ranged()) {
            return TargetClass.RANGED;
        }
        return TargetClass.MELEE;
    }

    public List<TargetClass> orderFor(DefenderRole role, boolean archerPresent) {
        if (role == null) {
            throw new IllegalArgumentException("role не может быть null: порядок целей разный у лучника и мечника");
        }
        if (role == DefenderRole.MELEE && !archerPresent) {
            return priorityWithoutArcher.get(DefenderRole.MELEE);
        }
        return priority.get(role);
    }

    private static Map<DefenderRole, List<TargetClass>> checkedOrders(
            String field, Map<DefenderRole, List<TargetClass>> orders, boolean full) {
        Map<DefenderRole, List<TargetClass>> checked = new EnumMap<>(DefenderRole.class);
        for (Map.Entry<DefenderRole, List<TargetClass>> entry : orders.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new InvalidFieldException(
                        field, field + " содержит null: роль и список классов обязаны быть заданы");
            }
            if (entry.getValue().isEmpty()) {
                throw new InvalidFieldException(
                        field, field + " для " + entry.getKey() + " пуст: отряд без порядка целей бьёт кого попало");
            }
            Set<TargetClass> seen = new HashSet<>();
            for (TargetClass cls : entry.getValue()) {
                if (cls == null || !seen.add(cls)) {
                    throw new InvalidFieldException(
                            field,
                            field + " для " + entry.getKey()
                                    + " содержит дубль или null: повтор ломает тай-брейк целей");
                }
            }
            checked.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        if (full && (!checked.containsKey(DefenderRole.ARCHER) || !checked.containsKey(DefenderRole.MELEE))) {
            throw new InvalidFieldException(
                    field, field + " без ARCHER или MELEE: порядок целей обязан быть у обеих специализаций");
        }
        if (!full && !checked.containsKey(DefenderRole.MELEE)) {
            throw new InvalidFieldException(
                    field, field + " без MELEE: мечник без лучника обязан знать свой порядок целей");
        }
        return Map.copyOf(checked);
    }
}
