package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class LeaseBypassGuardTests {
    private static final Path REGISTRY = Path.of("guards/src/test/resources/lease-bypass.txt");
    private static final Pattern CALL = Pattern.compile("\\.markTemporary\\s*\\(");

    @Test
    void markTemporary_EveryCurrentCaller_DeclaredForStageTwo() {
        Map<String, String> sources = mainSources();
        Map<String, String> declared = registry();

        assertEquals(
                List.of(),
                issues(sources, declared),
                "Временная правка мира идёт мимо LeaseBook, но не объявлена: после смерти духа или рестарта её "
                        + "клетки не попадут в аренду, скан не увидит уборку, а действующие подмости чужого духа "
                        + "будут снесены. Как надо: правка через LeaseBook.open/record либо строка в "
                        + "guards/src/test/resources/lease-bypass.txt с причиной «перевод на этапе 2». "
                        + "Правило: docs/ARCHITECTURE.md → «Аренда временной правки». Нарушения");
    }

    @Test
    void scan_NewCallWithoutException_IsReported() {
        Map<String, String> sources = Map.of(
                "com/theminespirit/neoforge/PillarColumn.java",
                        "class PillarColumn { void place() { data.markTemporary(pos); } }",
                "com/theminespirit/neoforge/NewScript.java",
                        "class NewScript { void place() { data.markTemporary(pos); } }");
        Map<String, String> declared =
                Map.of("com/theminespirit/neoforge/PillarColumn.java", "столб: перевод на этапе 2");

        assertTrue(
                issues(sources, declared).stream()
                        .anyMatch(issue -> issue.contains("NewScript.java") && issue.contains("мимо LeaseBook")),
                "Сторож ослеп: новый вызов markTemporary без строки реестра обязан попасть в нарушения");
    }

    @Test
    void scan_DeclaredLegacyCall_IsAccepted() {
        Map<String, String> sources = Map.of(
                "com/theminespirit/neoforge/PillarColumn.java",
                "class PillarColumn { void place() { data.markTemporary(pos); } }");

        assertEquals(
                List.of(),
                issues(sources, Map.of("com/theminespirit/neoforge/PillarColumn.java", "столб: перевод на этапе 2")),
                "Сторож ложно срабатывает: текущее место с причиной «перевод на этапе 2» обязано приниматься");
    }

    @Test
    void scan_ExceptionWithoutStageReason_IsReported() {
        Map<String, String> sources = Map.of(
                "com/theminespirit/neoforge/PillarColumn.java",
                "class PillarColumn { void place() { data.markTemporary(pos); } }");

        assertTrue(
                issues(sources, Map.of("com/theminespirit/neoforge/PillarColumn.java", "пока так")).stream()
                        .anyMatch(issue -> issue.contains("перевод на этапе 2")),
                "Сторож ослеп: исключение без пометки о переводе на этап 2 прячет долг и обязано попасть в нарушения");
    }

    @Test
    void scan_StaleException_IsReported() {
        Map<String, String> sources = Map.of(
                "com/theminespirit/neoforge/PillarColumn.java",
                "class PillarColumn { void place() { data.markTemporary(pos); } }");

        assertTrue(
                issues(
                                sources,
                                Map.of(
                                        "com/theminespirit/neoforge/PillarColumn.java", "столб: перевод на этапе 2",
                                        "com/theminespirit/neoforge/Gone.java", "удалён: перевод на этапе 2"))
                        .stream()
                        .anyMatch(issue -> issue.contains("Gone.java") && issue.contains("не является")),
                "Сторож ослеп: строка реестра без живого вызова прячет фальшивое исключение");
    }

    static List<String> issues(Map<String, String> sources, Map<String, String> declared) {
        List<String> found = new ArrayList<>();
        Map<String, String> users = new TreeMap<>();
        sources.forEach((path, text) -> {
            if (CALL.matcher(text).find()) {
                users.put(path, text);
            }
        });
        for (String path : new TreeSet<>(users.keySet())) {
            if (!declared.containsKey(path)) {
                found.add(path + ": временная правка мимо LeaseBook без объявленного перевода");
            }
        }
        declared.forEach((path, reason) -> {
            if (!users.containsKey(path)) {
                found.add(path + ": исключение не является вызовом markTemporary");
            }
            if (reason == null || reason.isBlank()) {
                found.add(path + ": исключение без причины");
            } else if (!reason.toLowerCase(Locale.ROOT).contains("перевод на этапе 2")) {
                found.add(path + ": исключение без причины «перевод на этапе 2»");
            }
        });
        return found;
    }

    private static Map<String, String> mainSources() {
        Map<String, String> sources = new TreeMap<>();
        SourceFiles.javaUnder(SourceFiles.MAIN)
                .forEach((file, text) ->
                        sources.put(SourceFiles.MAIN.relativize(file).toString().replace('\\', '/'), text));
        return sources;
    }

    private static Map<String, String> registry() {
        Map<String, String> reasons = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(REGISTRY, StandardCharsets.UTF_8)) {
                String value = line.strip();
                if (value.isEmpty() || value.startsWith("#")) {
                    continue;
                }
                String[] parts = value.split("\\s*\\|\\s*", 2);
                if (parts.length != 2) {
                    throw new IllegalStateException(
                            "Строка реестра возврата к аренде не в формате " + "«путь | причина»: " + line);
                }
                reasons.put(parts[0], parts[1]);
            }
        } catch (IOException error) {
            throw new UncheckedIOException(
                    "Сторож не прочитал реестр " + REGISTRY + ": без него правило не проверить", error);
        }
        return reasons;
    }
}
