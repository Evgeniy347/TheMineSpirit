package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ParserBoundsGuardTests {
    private static final List<String> PARSERS =
            List.of("FactorScalesParser.java", "EnemyProfilesParser.java", "ModesParser.java", "WeightsParser.java");
    private static final Pattern BOUNDED_READ = Pattern.compile("\\.(?:number|integer)\\(\\s*[^)\\s][^)]*\\)");

    private static List<String> boundedReads(String file, String text) {
        List<String> found = new ArrayList<>();
        Matcher matcher = BOUNDED_READ.matcher(text);
        while (matcher.find()) {
            found.add(file + ":" + SourceFiles.lineOf(text, matcher.start()) + " " + matcher.group());
        }
        return found;
    }

    @Test
    void tacticsParsers_Scanned_ReadNumbersWithoutOwnBounds() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String parser : PARSERS) {
            offenders.addAll(boundedReads(parser, Files.readString(SourceFiles.path("core/tactics/" + parser))));
        }

        assertEquals(
                List.of(),
                offenders,
                "Парсер задаёт границы числа сам, а конструктор записи проверяет их тоже: правка одного места оставит "
                        + "другое старым (docs/CONVENTIONS.md: одно правило — одно место). Читай число без аргументов "
                        + "(number()/integer()), границы держит FactChecks.range в конструкторе, а ConfigNode.build "
                        + "превращает отказ в ошибку с путём в файле. Нарушители");
    }

    @Test
    void boundedReadPattern_NumberWithBounds_IsDetected() {
        assertEquals(
                1,
                boundedReads("X.java", "return node.get(\"a\").number(0, 1);").size(),
                "Сторож ослеп: чтение числа с границами number(0, 1) должно находиться");
        assertEquals(
                1,
                boundedReads("X.java", "return node.get(\"a\").integer(0, MAX);")
                        .size(),
                "Сторож ослеп: чтение целого с границами из константы должно находиться");
    }

    @Test
    void boundedReadPattern_ReadWithoutBounds_IsIgnored() {
        assertTrue(
                boundedReads("X.java", "return node.get(\"a\").number();").isEmpty(),
                "Сторож ложно срабатывает: чтение числа без границ — как раз то, что нужно");
    }
}
