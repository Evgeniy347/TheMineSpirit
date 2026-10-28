package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TestTimeLimitGuardTests {
    private static final Path SCRIPT = Path.of("scripts/test.sh");
    private static final Pattern DEFAULT = Pattern.compile(
            "^([A-Z_]+_(?:WARN|STOP))=\"\\$\\{THEMINESPIRIT_[A-Z_]+_SECONDS:-(\\d+)}\"", Pattern.MULTILINE);

    private static final Map<String, Integer> AGREED = Map.of(
            "UNIT_WARN", 600,
            "UNIT_STOP", 900,
            "UNIT_CPU_WARN", 90,
            "UNIT_CPU_STOP", 200,
            "GAMETEST_WARN", 120,
            "GAMETEST_STOP", 200,
            "GAMETEST_FULL_WARN", 180,
            "GAMETEST_FULL_STOP", 300);

    @Test
    void testScript_TimeLimits_EqualAgreedValues() {
        assertEquals(
                new TreeMap<>(AGREED),
                limits(read(SCRIPT)),
                "Пределы времени в scripts/test.sh разошлись с согласованными (секунды). Предел не поднимают, чтобы "
                        + "зеленел долгий прогон: ищут, что замедлило тесты. Поднять можно только с согласия владельца — "
                        + "правкой таблицы AGREED в этом стороже с причиной. Правило: docs/TESTING.md → «Время»");
    }

    @Test
    void limits_ScriptDefaults_AreParsed() {
        assertEquals(
                new TreeMap<>(Map.of("UNIT_STOP", 901, "GAMETEST_WARN", 5, "GAMETEST_FULL_STOP", 1200)),
                limits("""
                        UNIT_STOP="${THEMINESPIRIT_UNIT_STOP_SECONDS:-901}"
                          NOT_LIMIT="${OTHER:-3}"
                        GAMETEST_STALE="${THEMINESPIRIT_GAMETEST_STALE_SECONDS:-120}"
                        GAMETEST_WARN="${THEMINESPIRIT_GAMETEST_WARN_SECONDS:-5}"
                        GAMETEST_FULL_STOP="${THEMINESPIRIT_GAMETEST_FULL_STOP_SECONDS:-1200}"
                        """),
                "Сторож ослеп: строки пределов вида NAME=\"${THEMINESPIRIT_…_SECONDS:-N}\" обязаны разбираться");
    }

    private static Map<String, Integer> limits(String script) {
        Map<String, Integer> found = new TreeMap<>();
        Matcher matcher = DEFAULT.matcher(script);
        while (matcher.find()) {
            found.put(matcher.group(1), Integer.valueOf(matcher.group(2)));
        }
        return found;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Не прочитан " + path, e);
        }
    }
}
