package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SpotBugsDetectorConfigGuardTests {
    private static final Path CONVENTIONS =
            Path.of("build-logic/src/main/groovy/theminespirit.java-conventions.gradle");
    private static final Path REPORT = Path.of("docs/research/analyzers-cost.md");
    private static final Path EXCLUDE = Path.of("config/spotbugs/exclude.xml");
    private static final Pattern OMIT = Pattern.compile("omitVisitors\\s*=\\s*\\[([^\\]]*)]", Pattern.DOTALL);
    private static final Pattern DETECTOR =
            Pattern.compile("^\\|\\s*`([A-Za-z0-9_]+)`\\s*\\|.*\\|\\s*да\\s*\\|\\s*$", Pattern.MULTILINE);
    private static final Pattern QUOTED = Pattern.compile("'([^']+)'");

    @Test
    void omittedVisitors_ConfigAndReport_Match() {
        String config = read(CONVENTIONS);
        String report = read(REPORT);
        Set<String> configured = omittedVisitors(config);
        Set<String> disabledInReport = reportDisabledDetectors(report);

        assertEquals(
                disabledInReport,
                configured,
                "Список omitVisitors в theminespirit.java-conventions.gradle разошёлся с таблицей отключённых "
                        + "детекторов в docs/research/analyzers-cost.md. Как надо: правишь один список — правишь "
                        + "второй и причину в комментарии; отключение только целиком (omitVisitors), не паттерном "
                        + "exclude.xml. Только в конфиге / только в отчёте");
        assertTrue(
                reportDetectors(report).size() >= 15,
                "Таблица кандидатов в отчёте сузилась: владелец должен видеть не меньше 15 детекторов с причиной "
                        + "«нужен/не нужен», иначе решение отключения примут вслепую");
    }

    @Test
    void spotbugs_OtherStrictness_NotLowered() {
        String config = read(CONVENTIONS);
        assertTrue(
                config.contains("ignoreFailures = false"),
                "ignoreFailures должен остаться false: анализатор обязан ронять сборку");
        assertTrue(
                config.contains("excludeFilter = configAt('spotbugs/exclude.xml')"),
                "фильтр исключений должен остаться config/spotbugs/exclude.xml");
        assertFalse(
                Pattern.compile("(?m)^\\s*effort\\s*=").matcher(config).find(),
                "effort настраивать нельзя: понижение строгости — не способ уложиться в 30%");
        assertFalse(
                Pattern.compile("(?m)^\\s*reportLevel\\s*=").matcher(config).find(),
                "reportLevel настраивать нельзя: это тихо спрячет находки");
        assertFalse(
                Pattern.compile("(?m)^\\s*visitors\\s*=").matcher(config).find(),
                "visitors настраивать нельзя: белый список сузил бы анализ");
        assertFalse(config.contains("chooseVisitors ="), "chooseVisitors настраивать нельзя");
        assertTrue(
                config.contains("dependsOn tasks.named('spotbugsMain'), tasks.named('spotbugsTest')"),
                "staticAnalysis обязан по-прежнему зависеть от spotbugsMain и spotbugsTest");
        assertEquals(
                new TreeSet<>(List.of(
                        "EI_EXPOSE_REP",
                        "EI_EXPOSE_REP2",
                        "EI_EXPOSE_STATIC_REP2",
                        "MS_EXPOSE_REP",
                        "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE",
                        "VA_FORMAT_STRING_USES_NEWLINE")),
                excludePatterns(read(EXCLUDE)),
                "Набор исключений SpotBugs изменился: ослаблять его ради скорости запрещено, отключай детектор "
                        + "целиком в omitVisitors с причиной");
    }

    private static final Pattern STATIC_SYNCHRONIZED = Pattern.compile("\\bstatic\\s+synchronized\\b");
    private static final Pattern COORDINATE_MIDPOINT =
            Pattern.compile("\\(\\s*([A-Za-z_]\\w*[XZ])\\s*\\+\\s*([A-Za-z_]\\w*[XZ])\\s*\\)\\s*/\\s*2\\b");
    private static final Pattern EQUALS_STRING_LITERAL =
            Pattern.compile("([A-Za-z_$][\\w$]*|\\)|\\])\\s*\\.equals\\(\\s*\"");

    @Test
    void productionSources_StaticSynchronizedMethods_AreAbsent() {
        List<String> offenders = new ArrayList<>();
        SourceFiles.allMain()
                .forEach((file, text) -> offenders.addAll(
                        matches(STATIC_SYNCHRONIZED, SourceFiles.withoutTextBlocks(text), file.toString())));

        assertEquals(
                List.of(),
                offenders,
                "static synchronized держит замок класса: поток, ждущий его, останавливает весь сервер, а "
                        + "синхронизация висит на публичном API, куда может войти кто угодно. SpotBugs ловил это "
                        + "как USO_UNSAFE_STATIC_METHOD_SYNCHRONIZATION (BuildSquadRegistry, WorkReserveHub, "
                        + "PolicyConfig, b1523e0f). Как надо: private static final Object LOCK и synchronized (LOCK) "
                        + "внутри метода. Нарушители (файл:строка)");
    }

    @Test
    void productionSources_CoordinateMidpoint_SummedInLong() {
        List<String> offenders = new ArrayList<>();
        SourceFiles.allMain()
                .forEach((file, text) -> offenders.addAll(
                        matches(COORDINATE_MIDPOINT, SourceFiles.withoutTextBlocks(text), file.toString())));

        assertEquals(
                List.of(),
                offenders,
                "Середина двух координат X/Z считается как (a + b) / 2 в int: сумма переполняется, центр уезжает "
                        + "за край мира, и поиск идёт не там. SpotBugs ловил это как "
                        + "IM_AVERAGE_COMPUTATION_COULD_OVERFLOW (TreeSupport, FloatingScan, b003b3b2). Как надо: "
                        + "(int) (((long) a + b) / 2). Нарушители (файл:строка)");
    }

    @Test
    void sources_StringComparison_PutsLiteralFirst() {
        List<String> offenders = new ArrayList<>();
        List<Map<Path, String>> roots = List.of(SourceFiles.allMain(), SourceFiles.allTests());
        for (Map<Path, String> sources : roots) {
            sources.forEach((file, text) ->
                    offenders.addAll(equalsLiteralFindings(SourceFiles.withoutTextBlocks(text), file.toString())));
        }

        assertEquals(
                List.of(),
                offenders,
                "Сравнение переменной с литералом через variable.equals(\"…\") падает с NPE, если переменная null, "
                        + "и прячет проверку от читателя. PMD нашёл так 209 мест (LiteralsFirstInComparisons). "
                        + "Как надо: \"литерал\".equals(variable) — null-safe и читается как вопрос «равно ли». "
                        + "Нарушители (файл:строка)");
    }

    @Test
    void analyzerFindingsPatterns_Violations_AreDetected() {
        assertEquals(
                List.of("Sample.java:2"),
                matches(
                        STATIC_SYNCHRONIZED,
                        "class Sample {\n    static synchronized void run() { }\n}\n",
                        "Sample.java"),
                "Сторож ослеп: static synchronized обязан попадать в нарушители");
        assertEquals(
                List.of(),
                matches(STATIC_SYNCHRONIZED, "static void run() { synchronized (LOCK) { } }", "Sample.java"),
                "Сторож ложно срабатывает: замок-объект — правильная форма");
        assertEquals(
                List.of("Sample.java:1"),
                matches(COORDINATE_MIDPOINT, "int mid = (minX + maxX) / 2;", "Sample.java"),
                "Сторож ослеп: середина координат без long обязана попадать в нарушители");
        assertEquals(
                List.of(),
                matches(COORDINATE_MIDPOINT, "int mid = (int) (((long) minX + maxX) / 2);", "Sample.java"),
                "Сторож ложно срабатывает: сумма в long переполнения не даёт");
        assertEquals(
                List.of("Sample.java:1"),
                equalsLiteralFindings("ticket.id().equals(\"x\")", "Sample.java"),
                "Сторож ослеп: variable.equals(\"…\") обязан находиться");
        assertEquals(
                List.of(),
                equalsLiteralFindings("\"x\".equals(ticket.id())", "Sample.java"),
                "Сторож ложно срабатывает: литерал слева — правильная форма");
        assertEquals(
                List.of(),
                equalsLiteralFindings("Objects.equals(\"x\", ticket.id())", "Sample.java"),
                "Сторож ложно срабатывает: Objects.equals принимает литерал первым аргументом");
    }

    @Test
    void parser_DisabledAndEnabledRows_AreSeparated() {
        String report = """
                | Детектор | Что |
                |---|---|
                | `SerializableIdiom` | сериализация | да |
                | `FindNullDeref` | null | нет |
                """;
        String config = """
                spotbugs {
                    omitVisitors = ['SerializableIdiom']
                    ignoreFailures = false
                }
                """;
        assertEquals(
                Set.of("SerializableIdiom"),
                reportDisabledDetectors(report),
                "Сторож ослеп: строка «да» обязана попасть в список отключённых");
        assertEquals(
                Set.of("SerializableIdiom"),
                omittedVisitors(config),
                "Сторож ослеп: omitVisitors обязан разбираться из конфигурации");
        assertNotEquals(
                Set.of("SerializableIdiom"),
                reportDisabledDetectors(report.replace(
                        "| `SerializableIdiom` | сериализация | да |", "| `SerializableIdiom` | сериализация | нет |")),
                "Сторож ослеп: строка «нет» не должна попадать в список отключённых");
    }

    static Set<String> omittedVisitors(String config) {
        Matcher matcher = OMIT.matcher(stripComments(config));
        Set<String> found = new TreeSet<>();
        while (matcher.find()) {
            Matcher quoted = QUOTED.matcher(matcher.group(1));
            while (quoted.find()) {
                found.add(quoted.group(1));
            }
        }
        return found;
    }

    static Set<String> reportDisabledDetectors(String report) {
        Matcher matcher = DETECTOR.matcher(report);
        Set<String> found = new TreeSet<>();
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    static Set<String> reportDetectors(String report) {
        Matcher matcher = Pattern.compile("^\\|\\s*`([A-Za-z0-9_]+)`\\s*\\|", Pattern.MULTILINE)
                .matcher(report);
        Set<String> found = new TreeSet<>();
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    static Set<String> excludePatterns(String xml) {
        Matcher matcher = Pattern.compile("Bug pattern=\"([^\"]+)\"").matcher(xml);
        Set<String> patterns = new TreeSet<>();
        while (matcher.find()) {
            String value = matcher.group(1);
            Matcher parts = Pattern.compile("[^,]+").matcher(value);
            while (parts.find()) {
                patterns.add(parts.group().trim());
            }
        }
        return patterns;
    }

    static List<String> matches(Pattern pattern, String text, String file) {
        List<String> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            found.add(file + ":" + SourceFiles.lineOf(text, matcher.start()));
        }
        return found;
    }

    static List<String> equalsLiteralFindings(String text, String file) {
        List<String> found = new ArrayList<>();
        Matcher matcher = EQUALS_STRING_LITERAL.matcher(text);
        while (matcher.find()) {
            // lib: Objects.equals("литерал", value) — статический вызов, а не сравнение с переменной: не нарушение.
            if (!"Objects".equals(matcher.group(1))) {
                found.add(file + ":" + SourceFiles.lineOf(text, matcher.start()));
            }
        }
        return found;
    }

    private static String stripComments(String text) {
        return text.lines()
                .map(line -> {
                    int comment = line.indexOf("//");
                    return comment < 0 ? line : line.substring(0, comment);
                })
                .collect(Collectors.joining("\n"));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Сторож не смог прочитать " + path, e);
        }
    }
}
