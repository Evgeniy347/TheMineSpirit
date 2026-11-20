package com.theminespirit.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.gametest.framework.TestFunction;

final class GameTestSelector {
    private GameTestSelector() {}

    static List<String> parseFilters(String raw) {
        List<String> filters = new ArrayList<>();
        for (String part : raw.split(",", -1)) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                filters.add(trimmed.toLowerCase(Locale.ROOT));
            }
        }
        return filters;
    }

    static boolean excluded(String batch, List<String> excludes) {
        String lower = batch.toLowerCase(Locale.ROOT);
        return excludes.stream().anyMatch(lower::startsWith);
    }

    static boolean matches(TestFunction function, String filter, Map<String, Set<String>> classTests) {
        String batch = function.batchName().toLowerCase(Locale.ROOT);
        String test = function.testName().toLowerCase(Locale.ROOT);
        if (filter.equals(batch) || filter.equals(test)) {
            return true;
        }
        int dot = filter.indexOf('.');
        if (dot >= 0) {
            return filter.substring(dot + 1).equals(test)
                    && classTests
                            .getOrDefault(filter.substring(0, dot), Set.of())
                            .contains(test);
        }
        return classTests.getOrDefault(filter, Set.of()).contains(test);
    }

    static Map<String, Set<String>> classToTests() {
        return GameTestClassIndex.byClass();
    }
}
