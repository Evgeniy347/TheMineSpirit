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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

@SuppressWarnings("StringConcatToTextBlock")
class InterruptExitGuardTests {
    private static final String REGISTRY_FILE = "guards/src/test/resources/interrupt-exits.txt";
    private static final Path REGISTRY = Path.of(REGISTRY_FILE);
    private static final Path NEOFORGE = SourceFiles.path("neoforge");
    private static final String MUTATION = ".markTemporary(";
    private static final String PREFIX = "neoforge/";
    private static final Pattern CLASS = Pattern.compile("\\bclass\\s+([A-Z][A-Za-z0-9_]*)\\b");
    private static final Pattern TEST_METHOD = Pattern.compile("\\bvoid\\s+([A-Za-z0-9_]+)\\s*\\(");

    record Row(String writer, String script, String exit, String test, String reason) {}

    @Test
    void interruptMap_EveryTemporaryWriter_DeclaresExitAndInterruptTest() {
        Map<String, String> main = mainSources();

        assertEquals(
                List.of(),
                issues(writers(main), readRegistry(REGISTRY), main, testSources(), exceptions(REGISTRY)),
                "Скрипт временно меняет мир, но не объявил экстренный выход или не имеет теста прерывания: "
                        + "при голоде/угрозе/смерти/рестарте останутся блоки или застрявший дух, а граф их не увидит. "
                        + "Как надо: строка в guards/src/test/resources/interrupt-exits.txt (writer | script | "
                        + "exit | test | reason) и тест Class.method с Interrupt в имени; либо явное исключение "
                        + "с причиной. Правило: docs/ARCHITECTURE.md → «Экстренный выход механики», "
                        + "docs/research/interrupt-map.md. Нарушения");
    }

    @Test
    void scan_NewTemporaryWriterWithoutRow_IsReported() {
        Map<String, String> main = Map.of(
                "neoforge/NewScript.java", "class NewScript { void tick() { data.markTemporary(pos); } }",
                "neoforge/PillarUpScript.java", "class PillarUpScript {}");

        assertTrue(
                issues(Set.of("neoforge/NewScript.java"), List.of(), main, Map.of(), Map.of()).stream()
                        .anyMatch(issue -> issue.contains("без объявленного выхода")),
                "Сторож ослеп: новый вызов markTemporary без строки в реестре обязан попасть в нарушения");
    }

    @Test
    void scan_RowWithoutInterruptTest_IsReported() {
        Map<String, String> main = Map.of(
                "neoforge/PillarColumn.java", "class PillarColumn { void place() { data.markTemporary(pos); } }",
                "neoforge/PillarUpScript.java", "class PillarUpScript {}");
        List<Row> rows = List.of(new Row(
                "neoforge/PillarColumn.java", "PillarUpScript", "cancel: столб остаётся", "PillarTests.ok", "столб"));
        Map<String, String> tests = Map.of("PillarTests", "class PillarTests { void ok() {} }");

        assertTrue(
                issues(Set.of("neoforge/PillarColumn.java"), rows, main, tests, Map.of()).stream()
                        .anyMatch(issue -> issue.contains("без теста прерывания")),
                "Сторож ослеп: тест без Interrupt в имени не доказывает выход при прерывании");
    }

    @Test
    void scan_ExceptionWithReason_IsAccepted() {
        Map<String, String> main = Map.of(
                "neoforge/NewScript.java", "class NewScript { void tick() { data.markTemporary(pos); } }",
                "neoforge/PillarUpScript.java", "class PillarUpScript {}");

        assertEquals(
                List.of(),
                issues(
                        Set.of("neoforge/NewScript.java"),
                        List.of(),
                        main,
                        Map.of(),
                        Map.of("neoforge/NewScript.java", "демо-сцена, к поселению не относится")),
                "Сторож ложно срабатывает: явное исключение с причиной обязано приниматься");
    }

    @Test
    void scan_ExceptionWithoutReason_IsReported() {
        Map<String, String> main =
                Map.of("neoforge/NewScript.java", "class NewScript { void tick() { data.markTemporary(pos); } }");

        assertTrue(
                issues(
                                Set.of("neoforge/NewScript.java"),
                                List.of(),
                                main,
                                Map.of(),
                                Map.of("neoforge/NewScript.java", " "))
                        .stream()
                        .anyMatch(issue -> issue.contains("исключение без причины")),
                "Сторож ослеп: исключение без причины прячет нарушение и обязано попасть в нарушения");
    }

    @Test
    void scan_RowForUnknownScript_IsReported() {
        Map<String, String> main = Map.of(
                "neoforge/PillarColumn.java", "class PillarColumn { void place() { data.markTemporary(pos); } }");
        List<Row> rows = List.of(new Row(
                "neoforge/PillarColumn.java",
                "GoneScript",
                "cancel: ничего",
                "InterruptExitTests.pillarUp_InterruptedOnTop_TemporaryMarksAndPerchNeedStayVisible",
                "столб"));
        Map<String, String> tests = Map.of(
                "InterruptExitTests",
                "class InterruptExitTests { void pillarUp_InterruptedOnTop_TemporaryMarksAndPerchNeedStayVisible() {} }");

        assertTrue(
                issues(Set.of("neoforge/PillarColumn.java"), rows, main, tests, Map.of()).stream()
                        .anyMatch(issue -> issue.contains("нет класса GoneScript")),
                "Сторож ослеп: владелец правки должен существовать, иначе строка реестра — фикция");
    }

    static List<String> issues(
            Set<String> writers,
            List<Row> rows,
            Map<String, String> main,
            Map<String, String> tests,
            Map<String, String> exceptions) {
        List<String> found = new ArrayList<>();
        Set<String> declared = new LinkedHashSet<>();
        for (Row row : rows) {
            declared.add(row.writer());
        }
        for (String writer : writers) {
            if (!declared.contains(writer) && !exceptions.containsKey(writer)) {
                found.add("временная правка " + writer + " без объявленного выхода");
            }
        }
        for (Row row : rows) {
            if (!writers.contains(row.writer())) {
                found.add("строка " + row.writer() + " ссылается на файл без " + MUTATION);
            }
            if (!main.containsKey(row.writer())) {
                found.add("в реестре нет файла " + row.writer());
            }
            if (!hasClass(main, row.script())) {
                found.add("нет класса " + row.script() + " для правки " + row.writer());
            }
            if (row.exit() == null || row.exit().isBlank()) {
                found.add("правка " + row.writer() + " не говорит, что остаётся при прерывании");
            }
            if (!hasInterruptTest(row.test(), tests)) {
                found.add("правка " + row.writer() + " без теста прерывания: " + row.test());
            }
        }
        exceptions.forEach((path, reason) -> {
            if (!writers.contains(path)) {
                found.add("исключение " + path + " не является временной правкой мира");
            }
            if (reason == null || reason.isBlank()) {
                found.add("исключение без причины: " + path);
            }
        });
        return found;
    }

    private static boolean hasClass(Map<String, String> main, String script) {
        for (String source : main.values()) {
            Matcher matcher = CLASS.matcher(source);
            while (matcher.find()) {
                if (matcher.group(1).equals(script)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasInterruptTest(String ref, Map<String, String> tests) {
        String[] parts = ref.split("\\.", 2);
        if (parts.length != 2) {
            return false;
        }
        String source = tests.get(parts[0]);
        if (source == null) {
            return false;
        }
        Matcher matcher = TEST_METHOD.matcher(source);
        while (matcher.find()) {
            String method = matcher.group(1);
            if (method.equals(parts[1]) && method.toLowerCase(Locale.ROOT).contains("interrupt")) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> writers(Map<String, String> main) {
        Set<String> found = new LinkedHashSet<>();
        main.forEach((path, source) -> {
            if (source.contains(MUTATION)) {
                found.add(path);
            }
        });
        return found;
    }

    private static Map<String, String> mainSources() {
        Map<String, String> sources = new TreeMap<>();
        try (Stream<Path> files = Files.walk(NEOFORGE)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).toList()) {
                sources.put(PREFIX + NEOFORGE.relativize(file).toString().replace('\\', '/'), Files.readString(file));
            }
        } catch (IOException error) {
            throw new UncheckedIOException("Сторож не прочитал исходники духов: " + error.getMessage(), error);
        }
        return sources;
    }

    private static Map<String, String> testSources() {
        Map<String, String> sources = new TreeMap<>();
        for (Path root : SourceFiles.moduleTestRoots()) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.toString().endsWith("Tests.java"))
                        .toList()) {
                    sources.put(file.getFileName().toString().replace(".java", ""), Files.readString(file));
                }
            } catch (IOException error) {
                throw new UncheckedIOException("Сторож не прочитал тесты: " + error.getMessage(), error);
            }
        }
        return sources;
    }

    private static List<Row> readRegistry(Path path) {
        List<Row> rows = new ArrayList<>();
        for (String line : lines(path)) {
            if (line.startsWith("neoforge/")) {
                rows.add(parse(line));
            }
        }
        return rows;
    }

    private static Map<String, String> exceptions(Path path) {
        Map<String, String> found = new LinkedHashMap<>();
        for (String line : lines(path)) {
            if (line.startsWith("exception |")) {
                String[] parts = line.split("\\s*\\|\\s*", 3);
                if (parts.length == 3) {
                    found.put(parts[1], parts[2]);
                }
            }
        }
        return found;
    }

    private static Row parse(String line) {
        String[] parts = line.split("\\s*\\|\\s*", -1);
        if (parts.length != 5) {
            throw new IllegalStateException("Строка реестра прерываний не в формате writer | script | exit | test | "
                    + "reason (5 полей, а не " + parts.length + "): " + line);
        }
        return new Row(parts[0], parts[1], parts[2], parts[3], parts[4]);
    }

    private static List<String> lines(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                    .map(String::strip)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .toList();
        } catch (IOException error) {
            throw new UncheckedIOException(
                    "Сторож не прочитал реестр " + path + ": без него правило не проверить", error);
        }
    }
}
