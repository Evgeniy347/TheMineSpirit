package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class LimitAssertionGuardTests {
    private static final Pattern ASSERTION =
            Pattern.compile("(?<![\\w.])(assert(?:Equals|NotEquals|ArrayEquals))\\s*\\(");
    private static final Pattern CONSTANT_TOKEN =
            Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|0[xX][0-9a-fA-F_]+[lL]?|"
                    + "\\d[\\d_]*(?:\\.\\d[\\d_]*)?(?:[eE][-+]?\\d+)?[lLfFdD]?|"
                    + "true|false|null|[-+*/%()<>!=&|?:~^]");

    @Test
    void testSources_CurrentTests_HaveNoLiteralVersusLiteralAssertions() {
        List<String> offenders = new ArrayList<>();
        SourceFiles.allTests().forEach((file, text) -> {
            for (Finding finding : findings(text)) {
                offenders.add(file + ":" + finding.line() + " " + finding.call());
            }
        });

        assertEquals(
                List.of(),
                offenders,
                "Проверка предела, где ожидание и факт — оба литерала, зеленеет даже если production "
                        + "нарушил правило: она не читает код, который защищает. Ожидание — число в тесте, "
                        + "факт — из production (константа или вызов). Правило: docs/TESTING.md → «Имя теста "
                        + "и сообщение assertion». Нарушители");
    }

    @Test
    void assertionScanner_LiteralVersusLiteral_IsReported() {
        String sample = "        assert" + "Equals(24, 24, \"бюджет чтений обязан быть 24\");\n";

        assertTrue(
                !findings(sample).isEmpty(),
                "Сторож ослеп: литерал с обеих сторон не читает production и должен попадать в нарушители");
    }

    @Test
    void assertionScanner_ConstantExpressions_IsReported() {
        String sample = "        assert" + "NotEquals(\"cow_pen\", \"pig_pen\", \"id видов различаются\");\n";

        assertTrue(
                !findings(sample).isEmpty(),
                "Сторож ослеп: две строки-литерала — та же проверка литерала литералом, но нарушение " + "не найдено");
    }

    @Test
    void assertionScanner_ProductionFact_IsAccepted() {
        String sample = "        assert" + "Equals(24, BlockerProbe.BLOCK_BUDGET, \"бюджет чтений 24\");\n";

        assertEquals(
                List.of(),
                findings(sample),
                "Сторож ложно срабатывает: число в тесте плюс факт из production — законная проверка предела");
    }

    @Test
    void assertionScanner_LiteralInsideMessage_IsAccepted() {
        String sample = "        assert" + "Equals(24, BlockerProbe.BLOCK_BUDGET,\n"
                + "                \"пример в тексте не проверка\");\n";

        assertEquals(List.of(), findings(sample), "Сторож ложно срабатывает: текст сообщения не является проверкой");
    }

    private static List<Finding> findings(String text) {
        List<Finding> findings = new ArrayList<>();
        Matcher matcher = ASSERTION.matcher(text);
        while (matcher.find()) {
            if (isInsideStringOrComment(text, matcher.start())) {
                continue;
            }
            List<String> arguments = arguments(text, matcher.end());
            if (arguments.size() >= 2
                    && isConstantExpression(arguments.get(0))
                    && isConstantExpression(arguments.get(1))) {
                findings.add(new Finding(SourceFiles.lineOf(text, matcher.start()), matcher.group(1)));
            }
        }
        return findings;
    }

    private static List<String> arguments(String text, int afterOpenParen) {
        List<String> arguments = new ArrayList<>();
        int depth = 0;
        int start = afterOpenParen;
        for (int i = afterOpenParen; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(text, i, c);
            } else if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                if (depth == 0) {
                    arguments.add(text.substring(start, i));
                    return arguments;
                }
                depth--;
            } else if (c == ',' && depth == 0) {
                arguments.add(text.substring(start, i));
                start = i + 1;
            }
        }
        return arguments;
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

    private static boolean isConstantExpression(String expression) {
        Matcher matcher = CONSTANT_TOKEN.matcher(expression);
        int position = 0;
        boolean hasValue = false;
        while (matcher.find()) {
            if (!expression.substring(position, matcher.start()).isBlank()) {
                return false;
            }
            String token = matcher.group();
            if ("true".equals(token)
                    || "false".equals(token)
                    || "null".equals(token)
                    || token.startsWith("\"")
                    || token.startsWith("'")
                    || Character.isDigit(token.charAt(0))) {
                hasValue = true;
            }
            position = matcher.end();
        }
        return hasValue && expression.substring(position).isBlank();
    }

    private static boolean isInsideStringOrComment(String text, int position) {
        int i = 0;
        while (i < position) {
            char c = text.charAt(i);
            if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                int end = text.indexOf('\n', i + 2);
                if (end < 0 || end > position) {
                    return true;
                }
                i = end + 1;
            } else if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                int end = text.indexOf("*/", i + 2);
                if (end < 0 || end + 2 > position) {
                    return true;
                }
                i = end + 2;
            } else if (text.startsWith("\"\"\"", i)) {
                int end = text.indexOf("\"\"\"", i + 3);
                if (end < 0 || end + 3 > position) {
                    return true;
                }
                i = end + 3;
            } else if (c == '"' || c == '\'') {
                int end = skipLiteral(text, i, c);
                if (end >= position) {
                    return true;
                }
                i = end + 1;
            } else {
                i++;
            }
        }
        return false;
    }

    private record Finding(int line, String call) {}
}
