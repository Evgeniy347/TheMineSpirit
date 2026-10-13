package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class AcquireAttemptsGuardTests {
    private static final Path NEOFORGE = SourceFiles.MAIN.resolve("com/theminespirit/neoforge");
    private static final Pattern ATTEMPT_CONSTANT =
            Pattern.compile("static\\s+final\\s+int\\s+\\w*ATTEMPT\\w*\\s*=", Pattern.CASE_INSENSITIVE);

    private static List<String> attemptConstants(String file, String text) {
        List<String> found = new ArrayList<>();
        Matcher matcher = ATTEMPT_CONSTANT.matcher(text);
        while (matcher.find()) {
            found.add(file + ":" + SourceFiles.lineOf(text, matcher.start()) + " " + matcher.group().trim());
        }
        return found;
    }

    @Test
    void neoforgeSources_AttemptConstants_DeclaredOnlyInCore() {
        List<String> offenders = new ArrayList<>();
        SourceFiles.javaUnder(NEOFORGE).forEach((file, text) ->
                offenders.addAll(attemptConstants(file.getFileName().toString(), text)));

        assertEquals(List.of(), offenders,
                "Адаптер neoforge не задаёт свои константы попыток: предел пересборок acquire живёт в "
                        + "CatalogLimits.ACQUIRE_MAX_ATTEMPTS (core), и AcquireScript читает его оттуда. Цена "
                        + "дублирования: правка одного места оставит другое старым, acquire в мире и тесты в core "
                        + "разойдутся в числе попыток. Нарушители");
    }

    @Test
    void attemptConstantPattern_ConstantWithAttemptInName_IsDetected() {
        assertEquals(1, attemptConstants("X.java", "static final int ACQUIRE_ATTEMPTS = 3;").size(),
                "Сторож ослеп: константа с ATTEMPT в имени должна находиться");
        assertEquals(1, attemptConstants("X.java", "static final int MAX_ATTEMPTS = 5;").size(),
                "Сторож ослеп: константа MAX_ATTEMPTS должна находиться");
    }

    @Test
    void attemptConstantPattern_UnrelatedConstants_AreIgnored() {
        assertTrue(attemptConstants("X.java", "static final int CONTAINER_RADIUS_DEFAULT = 8;").isEmpty(),
                "Сторож ложно срабатывает: константа без ATTEMPT в имени — не нарушение");
        assertTrue(attemptConstants("X.java", "int attempts = 0;").isEmpty(),
                "Сторож ложно срабатывает: локальная переменная attempts — не константа предела");
    }
}
