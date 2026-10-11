package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class AssertionMessageGuardTests {
    private static final Map<String, Integer> ARGUMENTS_WITH_MESSAGE = Map.ofEntries(
            Map.entry("assertTrue", 2),
            Map.entry("assertFalse", 2),
            Map.entry("assertNull", 2),
            Map.entry("assertEquals", 3),
            Map.entry("assertNotEquals", 3),
            Map.entry("assertSame", 3),
            Map.entry("assertNotSame", 3),
            Map.entry("assertArrayEquals", 3),
            Map.entry("assertIterableEquals", 3),
            Map.entry("assertLinesMatch", 3),
            Map.entry("assertThrows", 3),
            Map.entry("assertInstanceOf", 3));
    private static final Pattern ASSERT_CALL = Pattern.compile("(?<![\\w.])(assert[A-Z]\\w*)\\s*\\(");

    @Test
    void testSources_EveryAssertion_CarriesMessage() {
        List<String> offenders = new ArrayList<>();
        SourceFiles.javaUnder(SourceFiles.TEST).forEach((file, text) -> {
            for (String call : callsWithoutMessage(text)) {
                offenders.add(file + ":" + call);
            }
        });

        assertEquals(List.of(), offenders,
                "Проверка без сообщения при падении показывает только «expected X but was Y»: не видно, какой "
                        + "контракт сломан, что от этого ломается в игре и что перепроверить. Каждая проверка несёт "
                        + "последним аргументом сообщение на русском — какой контракт защищается и почему. "
                        + "Пример: assert" + "Equals(2, plan.steps().size(), \"план рубки обязан начинаться со scan: "
                        + "без скана дух не знает, где деревья\"). Правило: docs/TESTING.md → «Имя теста и "
                        + "сообщение assertion». Нарушители (строка и вызов)");
    }

    @Test
    void assertionParser_CallWithoutMessage_IsReported() {
        String sample = "        assert" + "Equals(List.of(1, 2), values);\n";

        assertEquals(List.of("1 assertEquals"), callsWithoutMessage(sample),
                "Сторож ослеп: assertEquals с двумя аргументами (запятые внутри List.of не в счёт) — нарушение");
    }

    @Test
    void assertionParser_MessageWithCommasAndParentheses_IsAccepted() {
        String sample = "        assert" + "True(map.containsKey(key(1, 2)),\n"
                + "                \"ключ (1, 2) обязан быть: иначе \\\"скан\\\" не найдёт блок, а план упадёт\");\n";

        assertEquals(List.of(), callsWithoutMessage(sample),
                "Сторож ложно срабатывает: сообщение есть, запятые и скобки внутри строки и вложенных вызовов не в счёт");
    }

    @Test
    void assertionParser_LambdaArgumentWithMessage_IsAccepted() {
        String sample = "        assert" + "Throws(IllegalStateException.class, () -> run(a, b), \"повтор обязан падать\");\n";

        assertEquals(List.of(), callsWithoutMessage(sample),
                "Сторож ложно срабатывает: лямбда с запятыми внутри скобок — один аргумент");
    }

    private static List<String> callsWithoutMessage(String text) {
        List<String> offenders = new ArrayList<>();
        Matcher matcher = ASSERT_CALL.matcher(text);
        while (matcher.find()) {
            Integer required = ARGUMENTS_WITH_MESSAGE.get(matcher.group(1));
            if (required != null && countArguments(text, matcher.end()) < required) {
                offenders.add(SourceFiles.lineOf(text, matcher.start()) + " " + matcher.group(1));
            }
        }
        return offenders;
    }

    private static int countArguments(String text, int afterOpenParen) {
        int depth = 0;
        int commas = 0;
        boolean empty = true;
        for (int i = afterOpenParen; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(text, i, c);
                empty = false;
            } else if (c == '(' || c == '[' || c == '{') {
                depth++;
                empty = false;
            } else if (c == ')' || c == ']' || c == '}') {
                if (depth == 0) {
                    return empty ? 0 : commas + 1;
                }
                depth--;
            } else if (c == ',' && depth == 0) {
                commas++;
            } else if (!Character.isWhitespace(c)) {
                empty = false;
            }
        }
        return commas + 1;
    }

    private static int skipLiteral(String text, int start, char quote) {
        for (int i = start + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == quote) {
                return i;
            }
        }
        return text.length();
    }
}
