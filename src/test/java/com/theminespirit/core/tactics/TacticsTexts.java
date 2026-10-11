package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class TacticsTexts {
    private TacticsTexts() {
    }

    static String read(String name) {
        return TacticsResources.read(name);
    }

    static String mutate(String text, String from, String to) {
        assertContains(text, from);
        return text.replace(from, to);
    }

    static String mutateFirst(String text, String from, String to) {
        assertContains(text, from);
        int index = text.indexOf(from);
        return text.substring(0, index) + to + text.substring(index + from.length());
    }

    private static void assertContains(String text, String fragment) {
        assertTrue(text.contains(fragment),
                "Подлог в тесте не применился: в файле нет фрагмента «" + fragment + "», тест мутаций стал бесполезным");
    }
}
