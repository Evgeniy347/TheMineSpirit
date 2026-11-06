package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class GameTestTimeoutGuardTests {
    private static final Pattern GAMETEST_WITH_TIMEOUT = Pattern.compile(
            "@(?:GameTest|SceneStep)\\s*\\([^)]*timeoutTicks\\s*=\\s*([^,)]+?)\\s*[,)]",
            Pattern.MULTILINE | Pattern.DOTALL);
    private static final Pattern STATIC_FINAL_INT = Pattern.compile(
            "(?:private\\s+|public\\s+|protected\\s+)?static\\s+final\\s+int\\s+([A-Za-z_]\\w*)\\s*=\\s*([0-9_]+)\\s*;");
    private static final Pattern STATIC_IMPORT =
            Pattern.compile("import\\s+static\\s+([\\w.]+)\\.([A-Za-z_]\\w*)\\s*;");
    private static final Pattern CLASS_DECL = Pattern.compile("(?:class|interface|enum|record)\\s+([A-Za-z_]\\w*)");
    private static final Pattern NEXT_TEST = Pattern.compile("@(?:GameTest|SceneStep)\\s*\\(");
    private static final int SANE_TIMEOUT_LIMIT = 400;

    @Test
    void gameTestTimeout_Over400_NoJustification() {
        List<String> offenders = new ArrayList<>();
        collectOffenders(SourceFiles.javaUnder(SourceFiles.GAME_TESTS), offenders);

        assertEquals(
                List.of(),
                offenders,
                "GameTest с timeoutTicks > 400 обязан иметь комментарий-обоснование с словом «флак» "
                        + "перед аннотацией: иначе тайм-аут прячет зависший тест (docs/TESTING.md → "
                        + "«Время тестов»: timeoutTicks ≈ 2× фактических тиков). Сторож видит только литерал "
                        + "или static final int константу; любое другое выражение — нарушение с тем же "
                        + "сообщением. Нарушители");
    }

    @Test
    void gameTestTimeoutPattern_Ticks600_NoComment() {
        String sample = """
                        class Sample {
                            @GameTest(template = "empty", timeoutTicks = 600)
                            public static void spawn_spiritType_Lives(GameTestHelper helper) {
                            }
                        }
                        """;

        assertEquals(
                List.of("timeoutTicks=600"),
                timeoutsOf(sample),
                "Сторож ослеп: timeoutTicks = 600 без комментария должно попадать в нарушители");
    }

    @Test
    void gameTestTimeoutPattern_Ticks600_WithFlakComment() {
        String sample = """
                        class Sample {
                            // [флак] под нагрузкой: ждём сдвиг набора через succeedWhen
                            @GameTest(template = "empty", timeoutTicks = 600)
                            public static void spawn_spiritType_Lives(GameTestHelper helper) {
                            }
                        }
                        """;

        assertEquals(
                List.of(),
                timeoutsOf(sample),
                "Сторож ложно срабатывает: timeoutTicks = 600 с комментарием-обоснованием — законно");
    }

    @Test
    void gameTestTimeoutPattern_Ticks400_NoComment() {
        String sample = """
                        class Sample {
                            @GameTest(template = "empty", timeoutTicks = 400)
                            public static void spawn_spiritType_Lives(GameTestHelper helper) {
                            }
                        }
                        """;

        assertEquals(
                List.of(),
                timeoutsOf(sample),
                "Сторож ложно срабатывает: timeoutTicks = 400 — граница, комментарий не нужен");
    }

    @Test
    void gameTestTimeoutPattern_MultilineAnnotation_IsReported() {
        String sample = """
                        class Sample {
                            @GameTest(template = "empty",
                                    timeoutTicks = 600)
                            public static void spawn_spiritType_Lives(GameTestHelper helper) {
                            }
                        }
                        """;

        assertEquals(
                List.of("timeoutTicks=600"),
                timeoutsOf(sample),
                "Сторож ослеп: многострочная @GameTest( с timeoutTicks = 600 без комментария должна попадать в нарушители");
    }

    @Test
    void gameTestTimeoutPattern_Ticks600_WithFlakMarker() {
        String sample = """
                        class Sample {
                            // [флак] под нагрузкой: ждём сдвиг набора через thenWaitUntil
                            @GameTest(template = "empty", timeoutTicks = 600)
                            public static void spawn_spiritType_Lives(GameTestHelper helper) {
                            }
                        }
                        """;

        assertEquals(
                List.of(),
                timeoutsOf(sample),
                "Сторож ложно срабатывает: timeoutTicks = 600 с меткой [флак] — законно");
    }

    @Test
    void gameTestTimeoutPattern_SceneStep600_NoComment() {
        String sample = """
                        class Sample {
                            @SceneStep(template = "arena", timeoutTicks = 600)
                            public static void spawn_spiritType_Lives(GameTestHelper helper) {
                            }
                        }
                        """;

        assertEquals(
                List.of("timeoutTicks=600"),
                timeoutsOf(sample),
                "Сторож ослеп: бюджет шага укрупнённой сцены 600 без [флак] обязан попадать в нарушители — шаг "
                        + "тот же бывший тест, и его тайм-аут так же прячет зависание");
    }

    @Test
    void gameTestTimeoutPattern_SceneOfSteps_IsClean() {
        String sample = """
                        class Sample {
                            @GameTest(template = "arena", batch = "scene_chop_arena", timeoutTicks = 9000)
                            public static void chopScene_ArenaSteps_AllPass(GameTestHelper helper) {
                                SceneSteps.run(helper, List.of("arena"), ChopTreeGameTests.class);
                            }
                        }
                        """;

        assertEquals(
                List.of(),
                timeoutsOf(sample),
                "Сторож ложно срабатывает: тайм-аут укрупнённой сцены — сумма бюджетов шагов, его сверяет "
                        + "SceneSteps при запуске");
    }

    @Test
    void gameTestTimeoutPattern_Literal300_IsClean() {
        String sample = """
                        class Sample {
                            @GameTest(template = "empty", timeoutTicks = 300)
                            public static void spawn_spiritType_Lives(GameTestHelper helper) {
                            }
                        }
                        """;

        assertEquals(List.of(), timeoutsOf(sample), "Сторож ложно срабатывает: литерал 300 ≤ 400 — законен без метки");
    }

    @Test
    void gameTestTimeoutPattern_SameClassConstant500_NoComment() {
        String sample = """
                        class Sample {
                            private static final int TIMEOUT_TICKS = 500;
                            @GameTest(template = "empty", timeoutTicks = TIMEOUT_TICKS)
                            public static void spawn_spiritType_Lives(GameTestHelper helper) {
                            }
                        }
                        """;

        assertEquals(
                List.of("timeoutTicks=500 (TIMEOUT_TICKS)"),
                timeoutsOf(sample),
                "Сторож ослеп: константа 500 без метки [флак] должна попадать в нарушители — "
                        + "иначе потолок прячется за именем и уходит от проверки");
    }

    @Test
    void gameTestTimeoutPattern_SameClassConstant500_WithFlakComment() {
        String sample = """
                        class Sample {
                            private static final int TIMEOUT_TICKS = 500;
                            // [флак] замер 240 тиков: запас 2× под нагрузкой
                            @GameTest(template = "empty", timeoutTicks = TIMEOUT_TICKS)
                            public static void spawn_spiritType_Lives(GameTestHelper helper) {
                            }
                        }
                        """;

        assertEquals(
                List.of(),
                timeoutsOf(sample),
                "Сторож ложно срабатывает: константа 500 с меткой [флак] и обоснованием — законна");
    }

    @Test
    void gameTestTimeoutPattern_SameClassConstant300_IsClean() {
        String sample = """
                        class Sample {
                            static final int TIMEOUT = 300;
                            @GameTest(template = "empty", timeoutTicks = TIMEOUT)
                            public static void spawn_spiritType_Lives(GameTestHelper helper) {
                            }
                        }
                        """;

        assertEquals(
                List.of(), timeoutsOf(sample), "Сторож ложно срабатывает: константа 300 ≤ 400 — законна без метки");
    }

    @Test
    void gameTestTimeout_ConstantJustifiedAtDeclaration_IsClean() {
        String sample = """
                        class Sample {
                            // [флак] замер 240 тиков: запас 2× под нагрузкой
                            static final int TIMEOUT_TICKS = 500;
                            @GameTest(template = "empty", timeoutTicks = TIMEOUT_TICKS)
                            public static void spawn_spiritType_Lives(GameTestHelper helper) {
                            }
                        }
                        """;

        assertEquals(
                List.of(),
                timeoutsOf(sample),
                "Сторож ложно срабатывает: обоснование стоит у объявления общей константы, "
                        + "а не у каждого GameTest — иначе 45 сайтов REST дублировали бы один комментарий");
    }

    @Test
    void gameTestTimeout_QualifiedConstantJustifiedAtDeclaration_IsClean() {
        String sample = """
                        class Sample {
                            @GameTest(template = "empty", timeoutTicks = Limits.TIMEOUT_TICKS)
                            public static void spawn_spiritType_Lives(GameTestHelper helper) {
                            }
                        }
                        class Limits {
                            // [флак] замер 240 тиков: запас 2× под нагрузкой
                            static final int TIMEOUT_TICKS = 500;
                        }
                        """;

        assertEquals(
                List.of(),
                timeoutsOf(sample),
                "Сторож ложно срабатывает: обоснование у объявления константы по полному имени "
                        + "закрывает все её использования");
    }

    @Test
    void gameTestTimeoutPattern_UnknownConstant_IsReported() {
        String sample = """
                        class Sample {
                            @GameTest(template = "empty", timeoutTicks = LIMIT_TICKS)
                            public static void spawn_spiritType_Lives(GameTestHelper helper) {
                            }
                        }
                        """;

        assertEquals(
                List.of("timeoutTicks=LIMIT_TICKS"),
                timeoutsOf(sample),
                "Сторож ослеп: timeoutTicks именем, значение которого он не нашёл, — нарушение: "
                        + "потолок уходит от проверки, сторож видит только литерал или static final int");
    }

    @Test
    void gameTestTimeoutPattern_ArithmeticExpression_IsReported() {
        String sample = """
                        class Sample {
                            @GameTest(template = "empty", timeoutTicks = 400 + 200)
                            public static void spawn_spiritType_Lives(GameTestHelper helper) {
                            }
                        }
                        """;

        assertEquals(
                List.of("timeoutTicks=400 + 200"),
                timeoutsOf(sample),
                "Сторож ослеп: timeoutTicks арифметикой, которую он не считает, — нарушение: "
                        + "потолок уходит от проверки, сторож видит только литерал или static final int");
    }

    @Test
    void gameTestTimeoutPattern_QualifiedConstant500_IsReported() {
        String sample = """
                        class Sample {
                            @GameTest(template = "empty", timeoutTicks = Limits.TIMEOUT_TICKS)
                            public static void spawn_spiritType_Lives(GameTestHelper helper) {
                            }
                        }
                        class Limits {
                            static final int TIMEOUT_TICKS = 500;
                        }
                        """;

        assertEquals(
                List.of("timeoutTicks=500 (Limits.TIMEOUT_TICKS)"),
                timeoutsOf(sample),
                "Сторож ослеп: константа по полному имени без метки [флак] должна попадать в нарушители");
    }

    private record Constant(int value, String text, int declarationOffset) {}

    private record ClassSource(String text, Map<String, Constant> constants) {}

    private static void collectOffenders(Map<Path, String> sources, List<String> offenders) {
        Map<String, ClassSource> classes = new LinkedHashMap<>();
        for (String text : sources.values()) {
            classes.putAll(classSourcesOf(text));
        }
        sources.forEach((file, text) -> {
            Matcher matcher = GAMETEST_WITH_TIMEOUT.matcher(text);
            while (matcher.find()) {
                if (isSceneOfSteps(text, matcher.end())) {
                    continue;
                }
                String expression = matcher.group(1).trim();
                Constant constant = resolve(expression, text, classes);
                if (constant == null) {
                    offenders.add(
                            file + ":" + SourceFiles.lineOf(text, matcher.start()) + " timeoutTicks=" + expression);
                } else if (constant.value() > SANE_TIMEOUT_LIMIT
                        && !hasAnyJustification(text, matcher.start(), constant)) {
                    offenders.add(file + ":" + SourceFiles.lineOf(text, matcher.start()) + " timeoutTicks="
                            + constant.value() + describe(expression));
                }
            }
        });
    }

    private static List<String> timeoutsOf(String text) {
        Map<String, ClassSource> classes = classSourcesOf(text);
        List<String> names = new ArrayList<>();
        Matcher matcher = GAMETEST_WITH_TIMEOUT.matcher(text);
        while (matcher.find()) {
            if (isSceneOfSteps(text, matcher.end())) {
                continue;
            }
            String expression = matcher.group(1).trim();
            Constant constant = resolve(expression, text, classes);
            if (constant == null) {
                names.add("timeoutTicks=" + expression);
            } else if (constant.value() > SANE_TIMEOUT_LIMIT && !hasAnyJustification(text, matcher.start(), constant)) {
                names.add("timeoutTicks=" + constant.value() + describe(expression));
            }
        }
        return names;
    }

    private static boolean isSceneOfSteps(String text, int annotationEnd) {
        Matcher next = NEXT_TEST.matcher(text);
        int end = next.find(annotationEnd) ? next.start() : text.length();
        return text.substring(annotationEnd, end).contains("SceneSteps.run(");
    }

    private static boolean hasAnyJustification(String text, int annotationStart, Constant constant) {
        if (hasJustification(text, annotationStart)) {
            return true;
        }
        return constant.declarationOffset() >= 0 && hasJustification(constant.text(), constant.declarationOffset());
    }

    private static String describe(String expression) {
        return expression.matches("[0-9_]+") ? "" : " (" + expression + ")";
    }

    private static Constant resolve(String expression, String text, Map<String, ClassSource> classes) {
        if (expression.matches("[0-9_]+")) {
            return new Constant(Integer.parseInt(expression.replace("_", "")), text, -1);
        }
        if (expression.matches("[A-Za-z_]\\w*")) {
            Constant local = localConstant(text, expression);
            if (local != null) {
                return local;
            }
            Constant imported = resolveStaticImport(expression, text, classes);
            if (imported != null) {
                return imported;
            }
            return resolveUniqueInPackage(expression, classes);
        }
        Matcher qualified = Pattern.compile("([A-Za-z_]\\w*)\\.([A-Za-z_]\\w*)").matcher(expression);
        if (qualified.matches()) {
            ClassSource owner = classes.get(qualified.group(1));
            return owner == null ? null : owner.constants().get(qualified.group(2));
        }
        return null;
    }

    private static Constant localConstant(String text, String name) {
        Map<String, ClassSource> classes = classSourcesOf(text);
        for (ClassSource source : classes.values()) {
            Constant constant = source.constants().get(name);
            if (constant != null) {
                return constant;
            }
        }
        return null;
    }

    private static Constant resolveStaticImport(String name, String text, Map<String, ClassSource> classes) {
        Matcher matcher = STATIC_IMPORT.matcher(text);
        while (matcher.find()) {
            if (matcher.group(2).equals(name)) {
                String owner = matcher.group(1).substring(matcher.group(1).lastIndexOf('.') + 1);
                ClassSource source = classes.get(owner);
                return source == null ? null : source.constants().get(name);
            }
        }
        return null;
    }

    private static Constant resolveUniqueInPackage(String name, Map<String, ClassSource> classes) {
        Constant found = null;
        for (ClassSource source : classes.values()) {
            Constant constant = source.constants().get(name);
            if (constant != null) {
                if (found != null && found.value() != constant.value()) {
                    return null;
                }
                found = constant;
            }
        }
        return found;
    }

    private static Map<String, ClassSource> classSourcesOf(String text) {
        List<String> names = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        Matcher matcher = CLASS_DECL.matcher(text);
        while (matcher.find()) {
            names.add(matcher.group(1));
            starts.add(matcher.end());
        }
        Map<String, ClassSource> result = new LinkedHashMap<>();
        for (int i = 0; i < names.size(); i++) {
            int end = i + 1 < names.size() ? starts.get(i + 1) : text.length();
            result.put(names.get(i), new ClassSource(text, constantsOf(text, starts.get(i), end)));
        }
        return result;
    }

    private static Map<String, Constant> constantsOf(String text, int start, int end) {
        Map<String, Constant> constants = new LinkedHashMap<>();
        Matcher matcher = STATIC_FINAL_INT.matcher(text);
        while (matcher.find()) {
            if (matcher.start() >= start && matcher.end() <= end) {
                constants.put(
                        matcher.group(1),
                        new Constant(Integer.parseInt(matcher.group(2).replace("_", "")), text, matcher.start()));
            }
        }
        return constants;
    }

    private static boolean hasJustification(String text, int annotationStart) {
        String[] lines = text.substring(0, annotationStart).split("\n", -1);
        for (int i = lines.length - 1; i >= Math.max(0, lines.length - 12); i--) {
            String line = lines[i].trim();
            if (line.startsWith("/*") || line.startsWith("*") || line.startsWith("//")) {
                if (line.contains("[флак]")) {
                    return true;
                }
            } else if (!line.isEmpty() && !line.startsWith("@")) {
                return false;
            }
        }
        return false;
    }
}
