package com.theminespirit.gametest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class SceneTestOwners {
    record TestClass(String simpleName, Set<String> tests, Set<String> references) {}

    private SceneTestOwners() {}

    static Map<String, Set<String>> index(List<TestClass> classes, Set<String> stepOwners) {
        Map<String, Set<String>> byFilter = new HashMap<>();
        for (TestClass type : classes) {
            if (type.tests().isEmpty()) {
                continue;
            }
            String name = type.simpleName().toLowerCase(Locale.ROOT);
            byFilter.computeIfAbsent(name, key -> new HashSet<>()).addAll(type.tests());
            for (String reference : type.references()) {
                if (!stepOwners.contains(reference)) {
                    continue;
                }
                String owner = simpleName(reference);
                byFilter.computeIfAbsent(owner.toLowerCase(Locale.ROOT), key -> new HashSet<>())
                        .addAll(type.tests());
            }
        }
        return byFilter;
    }

    private static String simpleName(String internalName) {
        return internalName.substring(internalName.lastIndexOf('/') + 1);
    }
}
