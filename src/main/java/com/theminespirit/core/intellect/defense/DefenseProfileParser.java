package com.theminespirit.core.intellect.defense;

import com.theminespirit.core.tactics.ConfigNode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class DefenseProfileParser {
    private static final Pattern TYPE_KEY = Pattern.compile("minecraft:[a-z_]+");

    private DefenseProfileParser() {
    }

    public static DefenseProfile fromJson(String json) {
        return parse(ConfigNode.root("priorities.json", json));
    }

    static DefenseProfile parse(ConfigNode root) {
        root.fields(new String[] {"classes", "priority", "priorityWithoutArcher"});
        Map<String, TargetClass> classes = classes(root.get("classes"));
        TargetClass fallback = classes.remove(DefenseProfile.DEFAULT_KEY);
        if (fallback == null) {
            throw root.get("classes").fail("нет записи '" + DefenseProfile.DEFAULT_KEY + "'",
                    "класс для неизвестного типа обязателен: без него новый враг не классифицировать");
        }
        Map<DefenderRole, List<TargetClass>> priority = orders(root.get("priority"));
        Map<DefenderRole, List<TargetClass>> withoutArcher = orders(root.get("priorityWithoutArcher"));
        return root.build(() -> new DefenseProfile(classes, fallback, priority, withoutArcher));
    }

    private static Map<String, TargetClass> classes(ConfigNode node) {
        Map<String, ConfigNode> entries = node.entries();
        if (entries.isEmpty()) {
            throw node.fail("пустая таблица",
                    "таблица классов целей не может быть пустой: без неё каждый враг — молчаливый fallback");
        }
        Map<String, TargetClass> classes = new LinkedHashMap<>();
        for (Map.Entry<String, ConfigNode> entry : entries.entrySet()) {
            String key = entry.getKey();
            if (!key.equals(DefenseProfile.DEFAULT_KEY) && !TYPE_KEY.matcher(key).matches()) {
                throw entry.getValue().fail("ключ '" + key + "' не подходит под шаблон minecraft:[a-z_]+ или default",
                        "тип врага — id сущности вида minecraft:zombie");
            }
            classes.put(key, entry.getValue().enumValue(TargetClass.class));
        }
        return classes;
    }

    private static Map<DefenderRole, List<TargetClass>> orders(ConfigNode node) {
        Map<String, ConfigNode> entries = node.entries();
        if (entries.isEmpty()) {
            throw node.fail("пустая таблица",
                    "таблица приоритетов не может быть пустой: без порядка целей бьют кого попало");
        }
        Map<DefenderRole, List<TargetClass>> orders = new EnumMap<>(DefenderRole.class);
        for (Map.Entry<String, ConfigNode> entry : entries.entrySet()) {
            DefenderRole role = node.enumValueOf(DefenderRole.class, entry.getKey(), "роль");
            if (orders.containsKey(role)) {
                throw entry.getValue().fail("роль '" + entry.getKey() + "' описана дважды",
                        "каждая специализация описывается ровно один раз");
            }
            List<TargetClass> order = new ArrayList<>();
            for (ConfigNode item : entry.getValue().nonEmptyItems()) {
                TargetClass cls = item.enumValue(TargetClass.class);
                if (order.contains(cls)) {
                    throw item.fail("класс '" + cls + "' описан дважды",
                            "повтор ломает тай-брейк целей: каждый класс встречается один раз");
                }
                order.add(cls);
            }
            orders.put(role, List.copyOf(order));
        }
        return orders;
    }
}
