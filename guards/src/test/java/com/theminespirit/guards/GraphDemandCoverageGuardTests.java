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
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class GraphDemandCoverageGuardTests {
    private static final Path DOCUMENT = Path.of("docs", "testing", "GRAPH-DEMAND.md");
    private static final int CHAINS = 50;
    private static final int MIN_COVERED = 25;
    private static final Pattern ROW =
            Pattern.compile("^\\|\\s*(\\d+)\\s*\\|([^|]*)\\|([^|]*)\\|\\s*(покрыто|есть|очередь)\\s*\\|\\s*$");
    private static final Pattern RAW_ROW = Pattern.compile("^\\|\\s*\\d+\\s*\\|([^|]*)\\|([^|]*)\\|([^|]*)\\|\\s*$");
    private static final Pattern REF = Pattern.compile("`([A-Za-z0-9_]+)\\.([A-Za-z0-9_]+)`");
    private static final Set<String> TESTED = Set.of("покрыто", "есть");
    private static final String QUEUE = "очередь";

    @Test
    void document_ChainsAndTests_AreConsistent() {
        assertEquals(
                List.of(),
                issues(read(DOCUMENT), testSources()),
                "Реестр цепочек спроса расходится с тестами: статус «покрыто/есть» без существующего теста "
                        + "или «очередь» с тестом делает покрытие выдумкой. Как надо: 50 строк, статус и "
                        + "существующий `Класс.метод`, у очереди — «—». Правило: docs/testing/GRAPH-DEMAND.md, "
                        + "AGENTS.md → «Правило — это сторож или тест». Нарушения");
    }

    @Test
    void document_TooFewCoveredChains_IsReported() {
        String text = table(1, QUEUE, "—");
        assertTrue(
                issues(text, Map.of()).stream().anyMatch(issue -> issue.contains("меньше " + MIN_COVERED)),
                "Сторож ослеп: документ без покрытых цепочек обязан сообщать о недоборе: " + issues(text, Map.of()));
    }

    @Test
    void row_MissingTest_IsReported() {
        String text = table(1, "покрыто", "—");
        assertTrue(
                issues(text, Map.of()).stream().anyMatch(issue -> issue.contains("без теста")),
                "Сторож ослеп: «покрыто» без теста обязано попасть в нарушения");
    }

    @Test
    void row_MissingMethod_IsReported() {
        String text = table(1, "есть", "`ATests.gone`");
        assertTrue(
                issues(text, Map.of("ATests", "class ATests { void ok() {} }")).stream()
                        .anyMatch(issue -> issue.contains("нет метода gone")),
                "Сторож ослеп: ссылка на удалённый тест обязана попасть в нарушения");
    }

    @Test
    void row_QueueWithTest_IsReported() {
        String text = table(1, QUEUE, "`ATests.ok`");
        assertTrue(
                issues(text, Map.of("ATests", "class ATests { void ok() {} }")).stream()
                        .anyMatch(issue -> issue.contains("очередь") && issue.contains("тест")),
                "Сторож ослеп: очередь с тестом обязана попасть в нарушения");
    }

    @Test
    void rows_MissingAndDuplicateChain_AreReported() {
        String text = table(1, QUEUE, "—").replace("| 50 |", "| 1 |");
        assertTrue(
                issues(text, Map.of()).stream().anyMatch(issue -> issue.contains("повторяется")),
                "Сторож ослеп: повтор номера цепочки обязан попасть в нарушения");
        assertTrue(
                issues(text, Map.of()).stream().anyMatch(issue -> issue.contains("нет цепочки 50")),
                "Сторож ослеп: пропущенная цепочка обязана попасть в нарушения");
    }

    @Test
    void row_UnknownStatus_IsReported() {
        String text = table(1, "сделано", "—");
        assertTrue(
                issues(text, Map.of()).stream().anyMatch(issue -> issue.contains("неизвестный статус")),
                "Сторож ослеп: статус вне покрыто/есть/очередь обязан попасть в нарушения");
    }

    private static String table(int covered, String status, String test) {
        StringBuilder text = new StringBuilder("| № | Цепочка | Тест | Статус |\n|---|---|---|---|\n");
        for (int chain = 1; chain <= CHAINS; chain++) {
            String rowStatus = chain <= covered ? status : QUEUE;
            String rowTest = chain <= covered ? test : "—";
            text.append("| ")
                    .append(chain)
                    .append(" | цепочка ")
                    .append(chain)
                    .append(" | ")
                    .append(rowTest)
                    .append(" | ")
                    .append(rowStatus)
                    .append(" |\n");
        }
        return text.toString();
    }

    static List<String> issues(String text, Map<String, String> sources) {
        List<String> issues = new ArrayList<>();
        Map<Integer, String> seen = new LinkedHashMap<>();
        int covered = 0;
        for (String line : text.lines().toList()) {
            Matcher row = ROW.matcher(line.strip());
            if (!row.matches()) {
                Matcher raw = RAW_ROW.matcher(line.strip());
                if (raw.matches()) {
                    String status = raw.group(3).strip();
                    issues.add(
                            status.isBlank()
                                    ? "строка '" + line.strip() + "' не разобрана: нужен статус покрыто/есть/очередь"
                                    : "неизвестный статус '" + status + "' в строке " + line.strip());
                }
                continue;
            }
            int number = Integer.parseInt(row.group(1));
            String test = row.group(3).strip();
            String status = row.group(4);
            if (seen.put(number, status) != null) {
                issues.add("цепочка " + number + " повторяется");
            }
            if (TESTED.contains(status)) {
                covered++;
                List<String> refs = refs(test);
                if (refs.isEmpty()) {
                    issues.add("цепочка " + number + ": статус " + status + " без теста");
                }
                for (String ref : refs) {
                    issues.addAll(check(ref, sources, number));
                }
            } else if (!"—".equals(test) && !"-".equals(test)) {
                issues.add("цепочка " + number + ": очередь не должна ссылаться на тест '" + test + "'");
            }
        }
        for (int chain = 1; chain <= CHAINS; chain++) {
            if (!seen.containsKey(chain)) {
                issues.add("нет цепочки " + chain + " в реестре");
            }
        }
        if (covered < MIN_COVERED) {
            issues.add("покрыто цепочек " + covered + " — меньше " + MIN_COVERED);
        }
        return issues;
    }

    private static List<String> refs(String test) {
        List<String> refs = new ArrayList<>();
        Matcher matcher = REF.matcher(test);
        while (matcher.find()) {
            refs.add(matcher.group(1) + "." + matcher.group(2));
        }
        return refs;
    }

    private static List<String> check(String ref, Map<String, String> sources, int chain) {
        int dot = ref.indexOf('.');
        String name = ref.substring(0, dot);
        String method = ref.substring(dot + 1);
        String source = sources.get(name);
        if (source == null) {
            return List.of("цепочка " + chain + ": нет класса теста " + name);
        }
        if (!Pattern.compile("\\bvoid\\s+" + Pattern.quote(method) + "\\s*\\(")
                .matcher(source)
                .find()) {
            return List.of("цепочка " + chain + ": в " + name + " нет метода " + method);
        }
        return List.of();
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException("Сторож не прочитал " + path + ": без реестра покрытие не проверить", error);
        }
    }

    private static Map<String, String> testSources() {
        Map<String, String> sources = new TreeMap<>();
        for (Path root : SourceFiles.moduleTestRoots()) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith("Tests.java"))
                        .toList()) {
                    String name = file.getFileName().toString().replace(".java", "");
                    sources.put(name, Files.readString(file, StandardCharsets.UTF_8));
                }
            } catch (IOException error) {
                throw new UncheckedIOException("Сторож не смог собрать тесты из " + root, error);
            }
        }
        return sources;
    }
}
