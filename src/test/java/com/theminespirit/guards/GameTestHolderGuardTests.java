package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class GameTestHolderGuardTests {
    private static final Path TEMPLATES = Path.of("src/main/resources/data/theminespirit/structure");
    private static final Pattern CLASS = Pattern.compile("\\b(?:class|record|enum|interface)\\s+(\\w+)");
    private static final Pattern GAMETEST_METHOD = Pattern.compile("@GameTest\\s*\\(");
    private static final Pattern GAMETEST_ANNOTATION = Pattern.compile("@GameTest\\s*\\(([^)]*)\\)");
    private static final Pattern HOLDER =
            Pattern.compile("@GameTestHolder\\s*\\(\\s*ModIdentity\\s*\\.\\s*MOD_ID\\s*\\)");
    private static final Pattern PREFIX = Pattern.compile("@PrefixGameTestTemplate\\s*\\(\\s*false\\s*\\)");
    private static final Pattern TEMPLATE_ATTRIBUTE =
            Pattern.compile("template\\s*=\\s*(?:\"([^\"]+)\"|([A-Za-z_][\\w.]*))");
    private static final Pattern STRING_CONSTANT =
            Pattern.compile("static\\s+final\\s+String\\s+(\\w+)\\s*=\\s*\"([^\"]*)\"");

    @Test
    void gameTestClasses_CurrentSources_HaveHolderPrefixAndExistingTemplate() {
        List<String> offenders = new ArrayList<>();
        collectOffenders(SourceFiles.javaUnder(SourceFiles.GAME_TESTS), templateNames(), offenders);

        assertEquals(
                List.of(),
                offenders,
                "Класс с @GameTest без @GameTestHolder(ModIdentity.MOD_ID) не регистрируется в пространстве "
                        + "мода, без @PrefixGameTestTemplate(false) шаблон ищется с префиксом класса и сцена "
                        + "не находится; шаблон, которого нет в data/theminespirit/structure/*.nbt, роняет "
                        + "пачку на старте. Правило: docs/TESTING.md → «Сцены». Нарушители");
    }

    @Test
    void gameTestClass_MissingHolder_IsReported() {
        String sample = """
                class Sample {
                    @GameTest(template = "arena")
                    public static void spawn_Spirit_Lives(GameTestHelper helper) {
                    }
                }
                """;

        assertTrue(
                offendersOf(sample, Set.of("arena")).stream().anyMatch(line -> line.contains("GameTestHolder")),
                "Сторож ослеп: класс с @GameTest без @GameTestHolder должен попадать в нарушители");
    }

    @Test
    void gameTestClass_MissingPrefix_IsReported() {
        String sample = """
                @GameTestHolder(ModIdentity.MOD_ID)
                class Sample {
                    @GameTest(template = "arena")
                    public static void spawn_Spirit_Lives(GameTestHelper helper) {
                    }
                }
                """;

        assertTrue(
                offendersOf(sample, Set.of("arena")).stream().anyMatch(line -> line.contains("PrefixGameTestTemplate")),
                "Сторож ослеп: класс с @GameTest без @PrefixGameTestTemplate(false) должен попадать " + "в нарушители");
    }

    @Test
    void gameTestClass_UnknownTemplate_IsReported() {
        String sample = """
                @GameTestHolder(ModIdentity.MOD_ID)
                @PrefixGameTestTemplate(false)
                class Sample {
                    @GameTest(template = "missing")
                    public static void spawn_Spirit_Lives(GameTestHelper helper) {
                    }
                }
                """;

        assertTrue(
                offendersOf(sample, Set.of("arena")).stream().anyMatch(line -> line.contains("missing")),
                "Сторож ослеп: шаблон без файла .nbt должен попадать в нарушители");
    }

    @Test
    void gameTestClass_KnownTemplateConstant_IsAccepted() {
        String sample = """
                @GameTestHolder(ModIdentity.MOD_ID)
                @PrefixGameTestTemplate(false)
                class Sample {
                    private static final String FIELD = "arena";

                    @GameTest(template = FIELD)
                    public static void spawn_Spirit_Lives(GameTestHelper helper) {
                    }
                }
                """;

        assertEquals(
                List.of(),
                offendersOf(sample, Set.of("arena")),
                "Сторож ложно срабатывает: шаблон из static final String с существующим .nbt — законное решение");
    }

    private static void collectOffenders(Map<Path, String> sources, Set<String> templates, List<String> offenders) {
        Map<String, Set<String>> constants = constantsOf(sources);
        sources.forEach((file, text) -> {
            for (Finding finding : findings(text, constants, templates)) {
                offenders.add(file + ":" + finding.line() + " — " + finding.problem());
            }
        });
    }

    private static List<String> offendersOf(String text, Set<String> templates) {
        List<String> problems = new ArrayList<>();
        for (Finding finding : findings(text, constantsOf(Map.of(Path.of("Sample.java"), text)), templates)) {
            problems.add(finding.problem());
        }
        return problems;
    }

    private static List<Finding> findings(String text, Map<String, Set<String>> constants, Set<String> templates) {
        List<Finding> findings = new ArrayList<>();
        for (ClassRange range : classes(text)) {
            if (!GAMETEST_METHOD.matcher(range.body()).find()) {
                continue;
            }
            String header = headerBefore(text, range.start());
            if (!HOLDER.matcher(header).find()) {
                findings.add(new Finding(
                        range.line(),
                        range.name() + ": класс с @GameTest не помечен @GameTestHolder(ModIdentity.MOD_ID)"));
            }
            if (!PREFIX.matcher(header).find()) {
                findings.add(new Finding(
                        range.line(), range.name() + ": класс с @GameTest не помечен @PrefixGameTestTemplate(false)"));
            }
            Matcher methods = GAMETEST_ANNOTATION.matcher(range.body());
            while (methods.find()) {
                int line = SourceFiles.lineOf(text, range.bodyStart() + methods.start());
                String template = templateOf(methods.group(1), constants);
                if (template == null) {
                    findings.add(new Finding(
                            line,
                            "шаблон @GameTest не задан строкой или "
                                    + "static final String: сцена не знает, какой .nbt загружать"));
                } else if (!templates.contains(template)) {
                    findings.add(new Finding(
                            line,
                            "шаблон «" + template + "» не существует: нет " + TEMPLATES + "/" + template + ".nbt; есть "
                                    + templates));
                }
            }
        }
        return findings;
    }

    private static List<ClassRange> classes(String text) {
        List<ClassRange> classes = new ArrayList<>();
        Matcher matcher = CLASS.matcher(text);
        while (matcher.find()) {
            int open = text.indexOf('{', matcher.end());
            int close = open < 0 ? -1 : matchingBrace(text, open);
            if (open < 0 || close < 0) {
                continue;
            }
            classes.add(new ClassRange(
                    matcher.group(1),
                    matcher.start(),
                    open + 1,
                    close,
                    text.substring(open + 1, close),
                    SourceFiles.lineOf(text, matcher.start())));
        }
        return classes;
    }

    private static String headerBefore(String text, int classStart) {
        int previousBodyEnd = text.lastIndexOf('}', classStart - 1);
        return text.substring(previousBodyEnd + 1, classStart);
    }

    private static int matchingBrace(String text, int openBrace) {
        int depth = 0;
        for (int i = openBrace; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String templateOf(String annotation, Map<String, Set<String>> constants) {
        Matcher matcher = TEMPLATE_ATTRIBUTE.matcher(annotation);
        if (!matcher.find()) {
            return null;
        }
        if (matcher.group(1) != null) {
            return matcher.group(1);
        }
        String identifier = matcher.group(2);
        if (identifier.contains(".")) {
            identifier = identifier.substring(identifier.lastIndexOf('.') + 1);
        }
        Set<String> values = constants.get(identifier);
        return values != null && values.size() == 1 ? values.iterator().next() : null;
    }

    private static Map<String, Set<String>> constantsOf(Map<Path, String> sources) {
        Map<String, Set<String>> constants = new LinkedHashMap<>();
        sources.values().forEach(text -> {
            Matcher matcher = STRING_CONSTANT.matcher(text);
            while (matcher.find()) {
                constants
                        .computeIfAbsent(matcher.group(1), ignored -> new LinkedHashSet<>())
                        .add(matcher.group(2));
            }
        });
        return constants;
    }

    private static Set<String> templateNames() {
        Set<String> names = new LinkedHashSet<>();
        try (Stream<Path> files = Files.list(TEMPLATES)) {
            files.filter(file -> file.getFileName().toString().endsWith(".nbt"))
                    .map(file -> file.getFileName().toString().replaceFirst("\\.nbt$", ""))
                    .sorted()
                    .forEach(names::add);
        } catch (IOException e) {
            throw new IllegalStateException("Сторож не смог прочитать шаблоны GameTest в " + TEMPLATES, e);
        }
        if (names.isEmpty()) {
            throw new IllegalStateException(
                    "Сторож не нашёл ни одного шаблона .nbt в " + TEMPLATES + ": без них GameTest не запустится");
        }
        return names;
    }

    private record ClassRange(String name, int start, int bodyStart, int bodyEnd, String body, int line) {}

    private record Finding(int line, String problem) {}
}
