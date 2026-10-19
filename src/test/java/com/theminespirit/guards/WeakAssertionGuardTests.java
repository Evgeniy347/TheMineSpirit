package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class WeakAssertionGuardTests {
    private static final Pattern WEAK_ASSERTION = Pattern.compile("\\bassert(NotNull|DoesNotThrow)\\s*\\(");

    @Test
    void testSources_Scanned_UseNoNotNullOrDoesNotThrowChecks() {
        List<String> offenders = new ArrayList<>();
        SourceFiles.javaUnder(SourceFiles.TEST).forEach((file, text) -> {
            Matcher matcher = WEAK_ASSERTION.matcher(text);
            while (matcher.find()) {
                offenders.add(file + ":" + SourceFiles.lineOf(text, matcher.start()) + " " + matcher.group());
            }
        });

        assertEquals(
                List.of(),
                offenders,
                "Проверки «не null» и «не упало» (assertNotNull, assertDoesNotThrow) зеленеют на любом неверном "
                        + "результате: план из одного не того скрипта или радиус, принятый молча, их проходят. "
                        + "Проверяй само поведение: assertEquals с ожидаемым значением или состоянием, "
                        + "assertThrows с ожидаемым типом и текстом. Пример: вместо проверки, что план не null, — "
                        + "сравнить список скриптов плана с ожидаемым. Правило: docs/TESTING.md → «Тест, который "
                        + "может упасть». Нарушители");
    }

    @Test
    void weakAssertionPattern_NotNullCall_IsDetected() {
        assertTrue(
                WEAK_ASSERTION
                        .matcher("        assert" + "NotNull(plan, \"сообщение\");")
                        .find(),
                "Сторож ослеп: вызов проверки «не null» должен находиться");
    }

    @Test
    void weakAssertionPattern_StaticImportLine_IsIgnored() {
        assertTrue(
                !WEAK_ASSERTION
                        .matcher("import static org.junit.jupiter.api.Assertions.assert" + "NotNull;")
                        .find(),
                "Сторож ложно срабатывает: строка импорта без вызова не нарушение, компилятор сам отметит лишний импорт");
    }
}
