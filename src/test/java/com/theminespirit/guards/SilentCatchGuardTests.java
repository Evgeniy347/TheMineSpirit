package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SilentCatchGuardTests {
    private static final Pattern EMPTY_CATCH = Pattern.compile("catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}");
    private static final Pattern RETHROW_ONLY_CATCH =
            Pattern.compile("catch\\s*\\(\\s*[\\w.|\\s]+?\\s+(\\w+)\\s*\\)\\s*\\{\\s*throw\\s+\\1\\s*;\\s*\\}");

    @Test
    void productionSources_Scanned_HaveNoEmptyCatchBlocks() {
        List<String> offenders = new ArrayList<>();
        SourceFiles.javaUnder(SourceFiles.MAIN).forEach((file, text) -> {
            Matcher matcher = EMPTY_CATCH.matcher(text);
            while (matcher.find()) {
                offenders.add(file + ":" + SourceFiles.lineOf(text, matcher.start()));
            }
        });

        assertEquals(List.of(), offenders,
                "Пустой catch прячет сбой: дух молча не выполняет задачу, а в логе нет ни строки, почему. "
                        + "Тихий fallback — авария, а не защита. Сделай одно из двух: пробрось исключение дальше "
                        + "с контекстом (что делали и с какими данными) или переведи задачу в FAILED с причиной "
                        + "и запиши её в лог. Правило: docs/CONVENTIONS.md → «Ошибки». Нарушители");
    }

    @Test
    void productionSources_Scanned_HaveNoCatchThatOnlyRethrows() {
        List<String> offenders = new ArrayList<>();
        SourceFiles.javaUnder(SourceFiles.MAIN).forEach((file, text) -> {
            Matcher matcher = RETHROW_ONLY_CATCH.matcher(text);
            while (matcher.find()) {
                offenders.add(file + ":" + SourceFiles.lineOf(text, matcher.start()));
            }
        });

        assertEquals(List.of(), offenders,
                "catch, который только пробрасывает то же исключение (catch (X e) { throw e; }), ничего не делает: "
                        + "исключение и так ушло бы дальше. Он только создаёт впечатление обработки и прячет, что "
                        + "обработки нет. Удали try/catch целиком или добавь в catch контекст: "
                        + "throw new X(\"что делали и с какими данными\", e). Нарушители");
    }

    @Test
    void rethrowOnlyPattern_SameVariableRethrown_IsDetected() {
        assertTrue(RETHROW_ONLY_CATCH.matcher(
                "} catch (PlanValidationException error) {\n    throw error;\n}").find(),
                "Сторож ослеп: catch, пробрасывающий ту же переменную, должен находиться");
    }

    @Test
    void rethrowOnlyPattern_WrappedWithContext_IsIgnored() {
        assertTrue(!RETHROW_ONLY_CATCH.matcher(
                "} catch (IOException error) {\n    throw new UncheckedIOException(\"чтение плана\", error);\n}").find(),
                "Сторож ложно срабатывает: обёртка с контекстом — это обработка");
    }

    @Test
    void emptyCatchPattern_EmptyBlockAcrossLines_IsDetected() {
        assertTrue(EMPTY_CATCH.matcher("try {\n    run();\n} catch (IllegalStateException ignored) {\n}\n").find(),
                "Сторож ослеп: пустой catch, разнесённый по строкам, должен находиться");
    }

    @Test
    void emptyCatchPattern_BlockWithStatement_IsIgnored() {
        assertTrue(!EMPTY_CATCH.matcher("} catch (IOException e) {\n    throw new UncheckedIOException(e);\n}").find(),
                "Сторож ложно срабатывает: catch с обработкой не нарушение");
    }
}
