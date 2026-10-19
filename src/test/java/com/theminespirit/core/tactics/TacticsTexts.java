package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TacticsTexts {
    private TacticsTexts() {}

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

    static String mutateRegex(String text, String regex, String replacement) {
        assertTrue(
                Pattern.compile(regex, Pattern.DOTALL).matcher(text).find(),
                "Подлог в тесте не применился: в файле нет фрагмента по шаблону " + regex);
        return Pattern.compile(regex, Pattern.DOTALL).matcher(text).replaceFirst(Matcher.quoteReplacement(replacement));
    }

    private static void assertContains(String text, String fragment) {
        assertTrue(
                text.contains(fragment),
                "Подлог в тесте не применился: в файле нет фрагмента «" + fragment
                        + "», тест мутаций стал бесполезным");
    }
}
