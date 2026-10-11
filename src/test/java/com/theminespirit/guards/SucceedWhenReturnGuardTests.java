package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SucceedWhenReturnGuardTests {
    private static final Pattern SUCCEED_WHEN_LAMBDA = Pattern.compile("succeedWhen\\(\\s*\\(\\)\\s*->\\s*\\{");
    private static final Pattern BARE_RETURN = Pattern.compile("\\breturn\\s*;");

    @Test
    void gameTestSources_SucceedWhenLambdas_HaveNoBareReturn() {
        List<String> offenders = new ArrayList<>();
        collect(SourceFiles.javaUnder(SourceFiles.GAME_TESTS), offenders);

        assertEquals(List.of(), offenders,
                "В лямбде succeedWhen стоит голый return: GameTestHelper.succeedWhen считает тест пройденным, как только "
                        + "лямбда завершилась без исключения, поэтому «if (ещё RUNNING) return;» засчитывает успех на первом "
                        + "же тике, и остальные проверки не выполняются никогда — тест зелёный при любом поведении. "
                        + "Как надо: условие ожидания — helper.assertTrue(готово, \"почему ещё не готово\"), оно бросает и "
                        + "succeedWhen повторит проверку на следующем тике (docs/TESTING.md → «Тест, который может упасть»). "
                        + "Нарушители (файл:строка)");
    }

    @Test
    void bareReturnPattern_ReturnInsideSucceedWhenLambda_IsReported() {
        String sample = "helper.succeedWhen(() -> {\n"
                + "    if (status == RUNNING) {\n"
                + "        return;\n"
                + "    }\n"
                + "    helper.assertTrue(done, \"готово\");\n"
                + "});\n";

        assertEquals(List.of(3), bareReturnLines(sample),
                "Сторож ослеп: голый return внутри лямбды succeedWhen должен находиться");
    }

    @Test
    void bareReturnPattern_ReturnOutsideSucceedWhen_IsIgnored() {
        String sample = "helper.onEachTick(() -> {\n"
                + "    if (session == null) {\n"
                + "        return;\n"
                + "    }\n"
                + "});\n"
                + "helper.succeedWhen(() -> {\n"
                + "    helper.assertTrue(done, \"готово\");\n"
                + "});\n";

        assertEquals(List.of(), bareReturnLines(sample),
                "Сторож ложно срабатывает: return в onEachTick и лямбда succeedWhen без return — не нарушение");
    }

    private static void collect(Map<Path, String> sources, List<String> offenders) {
        sources.forEach((file, text) -> {
            for (int line : bareReturnLines(text)) {
                offenders.add(file + ":" + line);
            }
        });
    }

    private static List<Integer> bareReturnLines(String text) {
        List<Integer> lines = new ArrayList<>();
        Matcher lambda = SUCCEED_WHEN_LAMBDA.matcher(text);
        while (lambda.find()) {
            int bodyStart = lambda.end() - 1;
            int bodyEnd = matchingBrace(text, bodyStart);
            Matcher bare = BARE_RETURN.matcher(text).region(bodyStart, bodyEnd);
            while (bare.find()) {
                lines.add(SourceFiles.lineOf(text, bare.start()));
            }
        }
        return lines;
    }

    private static int matchingBrace(String text, int open) {
        int depth = 0;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                return i;
            }
        }
        return text.length();
    }
}
