package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class OrderNotNumberGuardTests {
    private static final String TERM = "\\b\\w*(?:priority|Priority|weight|Weight|score|Score)"
            + "(?:\\s*\\([^()]*(?:\\([^()]*\\)[^()]*)*\\))?(?!\\s*[.(\\w])";
    private static final String NUMBER = "-?\\d[\\d_]*(?:\\.\\d+)?(?:[eE]-?\\d+)?[dDfFL]?\\b";
    private static final String OP = "(?:<=|>=|==|!=|<(?![<=])|>(?![>=]))";
    private static final Pattern TERM_THEN_NUMBER = Pattern.compile(TERM + "\\s*" + OP + "\\s*" + NUMBER);
    private static final Pattern NUMBER_THEN_TERM =
            Pattern.compile("(?<![\\w.])" + NUMBER + "\\s*" + OP + "\\s*(?:\\w+(?:\\([^()]*\\))?\\.)*" + TERM);
    private static final Pattern EQUALS_NUMBER =
            Pattern.compile("assert(?:Not)?Equals\\s*\\(\\s*" + NUMBER + "\\s*,\\s*[^,;]*?" + TERM);

    @Test
    void testSources_PriorityComparedWithNumber_AreAbsent() {
        List<String> offenders = new ArrayList<>();
        SourceFiles.allTests().forEach((file, text) -> {
            for (String hit : numericChecks(text)) {
                offenders.add(file + ":" + hit);
            }
        });

        assertEquals(
                List.of(),
                offenders,
                "Тест сравнивает приоритет, вес или счёт с числом: число замораживает решение (порог «откат ≥85» "
                        + "держал правило, из-за которого пять духов четыре часа висели в откате), правка весов в "
                        + "данных ломает десятки тестов, а число в допуске при неверном порядке даёт зелёный. Как "
                        + "надо: проверь порядок в ситуации теста — «A выбирается раньше B», «задача поднята» "
                        + "(GraphPlan.Task.raised()), «после события X A обгоняет B». Красный тест порядка — разбор "
                        + "адекватности решения: веса под тест не подгонять. Нарушители (строка и выражение)");
    }

    @Test
    void parser_PriorityAgainstLiteral_IsReported() {
        String sample = "        assert" + "True(task(plan, \"undo\").priority() >= 85, \"откат высоко\");\n"
                + "        assert" + "Equals(100, defend.priority(), \"потолок\");\n"
                + "        assert" + "True(0.5 < half.weight(), \"вес\");\n";

        assertEquals(
                List.of("1 priority() >= 85", "3 0.5 < half.weight()", "2 assert" + "Equals(100, defend.priority()"),
                numericChecks(sample),
                "Сторож ослеп: сравнение priority()/weight() с числом с любой стороны и assert" + "Equals(число, "
                        + "…priority()) — нарушения: " + numericChecks(sample));
    }

    @Test
    void parser_TasksComparedWithEachOther_AreAccepted() {
        String sample = "        assert" + "True(repair.priority() > guard.priority(), \"забор важнее охраны: 85\");\n"
                + "        assert" + "True(task(plan, \"eat\").raised(), \"еда поднята\");\n"
                + "        assert" + "Equals(List.of(\"a\", \"b\"), order(plan), \"порядок: 1 > 0\");\n";

        assertEquals(
                List.of(),
                numericChecks(sample),
                "Сторож ложно срабатывает: сравнение задач между собой, raised() и числа внутри строк — не нарушение");
    }

    private static List<String> numericChecks(String text) {
        String code = withoutStrings(text);
        List<String> hits = new ArrayList<>();
        for (Pattern pattern : List.of(TERM_THEN_NUMBER, NUMBER_THEN_TERM, EQUALS_NUMBER)) {
            Matcher matcher = pattern.matcher(code);
            while (matcher.find()) {
                int line = 1
                        + (int) code.substring(0, matcher.start())
                                .chars()
                                .filter(c -> c == '\n')
                                .count();
                hits.add(line + " " + matcher.group().replaceAll("\\s+", " "));
            }
        }
        return hits;
    }

    private static String withoutStrings(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean inString = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\' && i + 1 < text.length()) {
                    i++;
                    out.append("  ");
                    continue;
                }
                if (c == '"') {
                    inString = false;
                    out.append(c);
                    continue;
                }
                out.append(c == '\n' ? '\n' : ' ');
                continue;
            }
            if (c == '\'') {
                int end = text.indexOf('\'', i + (text.charAt(i + 1) == '\\' ? 3 : 2));
                out.append(" ".repeat(end - i + 1));
                i = end;
                continue;
            }
            if (c == '"') {
                inString = true;
            }
            out.append(c);
        }
        return out.toString();
    }
}
