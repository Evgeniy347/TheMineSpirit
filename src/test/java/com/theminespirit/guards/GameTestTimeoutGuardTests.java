package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class GameTestTimeoutGuardTests {
    private static final Pattern GAMETEST_WITH_TIMEOUT = Pattern.compile(
            "@GameTest\\s*\\([^\\n]*timeoutTicks\\s*=\\s*(\\d+)[^\\n]*\\)", Pattern.MULTILINE);
    private static final int SANE_TIMEOUT_LIMIT = 400;

    @Test
    void gameTestTimeout_Over400_NoJustification() {
        List<String> offenders = new ArrayList<>();
        collectOffenders(SourceFiles.javaUnder(SourceFiles.GAME_TESTS), offenders);

        assertEquals(List.of(), offenders,
                "GameTest с timeoutTicks > 400 обязан иметь комментарий-обоснование с словом «флак» "
                        + "перед аннотацией: иначе тайм-аут прячет зависший тест (docs/TESTING.md → "
                        + "«Время тестов»: timeoutTicks ≈ 2× фактических тиков). Нарушители");
    }

    @Test
    void gameTestTimeoutPattern_Ticks600_NoComment() {
        String sample = "class Sample {\n"
                + "    @GameTest(template = \"empty\", timeoutTicks = 600)\n"
                + "    public static void spawn_spiritType_Lives(GameTestHelper helper) {\n"
                + "    }\n"
                + "}\n";

        assertEquals(List.of("timeoutTicks=600"), timeoutsOf(sample),
                "Сторож ослеп: timeoutTicks = 600 без комментария должно попадать в нарушители");
    }

    @Test
    void gameTestTimeoutPattern_Ticks600_WithFlakComment() {
        String sample = "class Sample {\n"
                + "    // Флак под нагрузкой: ждём сдвиг набора через succeedWhen\n"
                + "    @GameTest(template = \"empty\", timeoutTicks = 600)\n"
                + "    public static void spawn_spiritType_Lives(GameTestHelper helper) {\n"
                + "    }\n"
                + "}\n";

        assertEquals(List.of(), timeoutsOf(sample),
                "Сторож ложно срабатывает: timeoutTicks = 600 с комментарием-обоснованием — законно");
    }

    @Test
    void gameTestTimeoutPattern_Ticks400_NoComment() {
        String sample = "class Sample {\n"
                + "    @GameTest(template = \"empty\", timeoutTicks = 400)\n"
                + "    public static void spawn_spiritType_Lives(GameTestHelper helper) {\n"
                + "    }\n"
                + "}\n";

        assertEquals(List.of(), timeoutsOf(sample),
                "Сторож ложно срабатывает: timeoutTicks = 400 — граница, комментарий не нужен");
    }

    private static void collectOffenders(Map<Path, String> sources, List<String> offenders) {
        sources.forEach((file, text) -> {
            Matcher matcher = GAMETEST_WITH_TIMEOUT.matcher(text);
            while (matcher.find()) {
                int timeout = Integer.parseInt(matcher.group(1));
                if (timeout > SANE_TIMEOUT_LIMIT && !hasJustification(text, matcher.start())) {
                    offenders.add(file + ":" + SourceFiles.lineOf(text, matcher.start()) + " timeoutTicks=" + timeout);
                }
            }
        });
    }

    private static boolean hasJustification(String text, int annotationStart) {
        String[] lines = text.substring(0, annotationStart).split("\n");
        for (int i = lines.length - 1; i >= Math.max(0, lines.length - 12); i--) {
            String line = lines[i].trim();
            if (line.startsWith("/*") || line.startsWith("*") || line.startsWith("//")) {
                if (line.toLowerCase().contains("флак")) {
                    return true;
                }
            } else if (!line.isEmpty() && !line.startsWith("@")) {
                return false;
            }
        }
        return false;
    }

    private static List<String> timeoutsOf(String text) {
        List<String> names = new ArrayList<>();
        Matcher matcher = GAMETEST_WITH_TIMEOUT.matcher(text);
        while (matcher.find()) {
            int timeout = Integer.parseInt(matcher.group(1));
            if (timeout > SANE_TIMEOUT_LIMIT && !hasJustification(text, matcher.start())) {
                names.add("timeoutTicks=" + timeout);
            }
        }
        return names;
    }
}
