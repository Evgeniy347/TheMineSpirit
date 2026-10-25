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

class TestHygieneGuardTests {
    private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:\\\\.|[^\"\\\\\\n])*\"");
    private static final Pattern COMMENT = Pattern.compile("//[^\\n]*|/\\*(?s:.*?)\\*/");
    private static final Pattern DISABLED = Pattern.compile("@Disabled\\b");
    private static final Pattern PRIVATE_REFLECTION =
            Pattern.compile("\\.setAccessible\\s*\\(|getDeclared(?:Field|Method|Constructor)\\s*\\(");
    private static final Pattern MUTABLE_STATIC_FIELD = Pattern.compile(
            "(?m)^[ \\t]*(?:@[\\w.]+(?:\\([^)]*\\))?[ \\t]+)*(?:(?:public|protected|private)[ \\t]+)?static[ \\t]+"
                    + "(?!final\\b)([^;=\\n]*?)(\\w+)[ \\t]*[;=]");
    private static final Pattern MUTABLE_STATIC_COLLECTION = Pattern.compile(
            "(?m)^[ \\t]*(?:@[\\w.]+(?:\\([^)]*\\))?[ \\t]+)*(?:(?:public|protected|private)[ \\t]+)?static[ \\t]+final[ \\t]+"
                    + "([^;=\\n]*?)(\\w+)[ \\t]*=[ \\t]*new[ \\t]*(?:HashMap|LinkedHashMap|TreeMap|ConcurrentHashMap|"
                    + "WeakHashMap|IdentityHashMap|EnumMap|ArrayList|LinkedList|CopyOnWriteArrayList|"
                    + "HashSet|LinkedHashSet|TreeSet|CopyOnWriteArraySet|ConcurrentSkipListMap|ConcurrentSkipListSet|"
                    + "ArrayDeque|ConcurrentLinkedQueue|PriorityQueue)\\b");

    // Единственное разрешённое изменяемое static-состояние: кэш прочитанных исходников наполняется один раз,
    // дальше только читается. Запись под другим ключом: ключ — «путь#имяПоля», адресно, чтобы переименование
    // поля или отказ от кэша валили сторож устаревшим исключением.
    private static final Map<String, String> STATIC_EXCEPTIONS = Map.of(
            "guards/SourceFiles.java#CACHE",
            "единственный общий кэш исходников: наполняется один раз, дальше чтение — не переносит состояние между тестами");

    @Test
    void testSources_Scanned_HaveNoDisabledTests() {
        List<String> offenders = new ArrayList<>();
        testSources().forEach((file, text) -> {
            Matcher matcher = DISABLED.matcher(code(text));
            while (matcher.find()) {
                offenders.add(file + ":" + SourceFiles.lineOf(code(text), matcher.start()));
            }
        });

        assertEquals(
                List.of(),
                offenders,
                "Тест помечен @Disabled: он не защищает поведение, о поломке узнают из игры, а не из прогона. "
                        + "Отключённый тест прячет регрессию и гниёт молча. Как надо: починить тест или удалить его — "
                        + "если поведение больше не нужно. Правило: docs/TESTING.md → «Сторожа». Нарушители");
    }

    @Test
    void testSources_Scanned_HaveNoReflectionIntoPrivateMembers() {
        List<String> offenders = new ArrayList<>();
        testSources().forEach((file, text) -> {
            Matcher matcher = PRIVATE_REFLECTION.matcher(code(text));
            while (matcher.find()) {
                offenders.add(file + ":" + SourceFiles.lineOf(code(text), matcher.start()));
            }
        });

        assertEquals(
                List.of(),
                offenders,
                "Тест лезет рефлексией в private (setAccessible(true) или getDeclaredField/Method/Constructor): "
                        + "проверяется не контракт, а внутренняя деталь, которая ломает тест при любом рефакторинге. "
                        + "Как надо: проверяй публичное поведение; если private нельзя проверить иначе — вынеси логику "
                        + "в тестируемый класс core. Правило: docs/TESTING.md → «Сторожа». Нарушители");
    }

    @Test
    void testSources_Scanned_HaveNoMutableStaticState() {
        List<String> offenders = mutableStaticOffenders(testSources());

        assertEquals(
                List.of(),
                offenders,
                "В тестах изменяемое static-состояние: прогон тестов становится зависимым от порядка, а параллельные "
                        + "тесты затирают данные друг друга. Как надо: поле — локальная переменная, поле экземпляра "
                        + "или @BeforeEach; неизменяемые константы — static final с Map.of/List.of/Set.of. "
                        + "Единственное исключение — guards.SourceFiles. Правило: docs/TESTING.md → «Сторожа». Нарушители");
    }

    @Test
    void disabledPattern_AnnotatedTest_IsReported() {
        String sample = "class Sample {\n    @Disabled\n    @Test\n    void x() {\n    }\n}\n";

        assertEquals(List.of(2), disabledLines(sample), "Сторож ослеп: @Disabled на тесте должен находиться");
    }

    @Test
    void reflectionPattern_AccessingPrivateMember_IsReported() {
        String sample = """
                        Field f = clazz.getDeclaredField("x");
                        f.setAccessible(true);
                        Method m = clazz.getDeclaredMethod("y");
                        """;

        assertEquals(
                3,
                reflectionCount(sample),
                "Сторож ослеп: рефлексия к private (setAccessible, getDeclaredField/Method) должна находиться");
    }

    @Test
    void mutableStaticField_PlainDeclaration_IsReported() {
        String sample = "class Sample {\n    private static int counter;\n}\n";

        assertEquals(
                List.of("counter"),
                staticFieldNames(sample),
                "Сторож ослеп: неизменяемое static-поле без final должно попадать в нарушители");
    }

    @Test
    void mutableStaticCollection_ConstructorInitializer_IsReported() {
        String sample = """
                        class Sample {
                            private static final Map<String, Integer> seen = new HashMap<>();
                            static final List<String> order = new ArrayList<>();
                        }
                        """;

        assertEquals(
                List.of("seen", "order"),
                staticFieldNames(sample),
                "Сторож ослеп: static final-коллекция, наполняемая через new HashMap/new ArrayList, изменяема");
    }

    @Test
    void immutableStatics_ConstantsAndMethods_AreIgnored() {
        String sample = """
                        class Sample {
                            private static final Pattern P = Pattern.compile("x");
                            private static final Map<String, Integer> M = Map.of("a", 1);
                            private static final List<String> L = List.of("a");
                            private static final BlockCoord C = new BlockCoord(0, 0, 0);
                            private static void helper() {
                            }
                            static {
                                int a = 1;
                            }
                        }
                        """;

        assertEquals(
                List.of(),
                staticFieldNames(sample),
                "Сторож ложно срабатывает: неизменяемые константы (Map.of/List.of, статический конструктор), "
                        + "static-метод и static-блок — не изменяемое состояние");
    }

    @Test
    void textBlockSample_InsideTestSource_IsNotCode() {
        String quotes = "\"\"\"";
        String source = "class Sample {\n    String s = " + quotes + "\n        static int seen;\n        " + quotes
                + ";\n    static int real;\n}\n";

        List<String> offenders = mutableStaticOffenders(Map.of(Path.of("Sample.java"), source), Map.of());

        assertEquals(
                List.of("Sample.java:5 изменяемое static-поле real"),
                offenders,
                "Образец кода в текстовом блоке — данные теста, а не код: иначе сторож ловит собственные образцы; "
                        + "вырезанный блок сохраняет переводы строк, иначе номер строки нарушителя съезжает");
    }

    @Test
    void staleException_NoLongerMatchingCode_IsReported() {
        Map<Path, String> sources = Map.of(Path.of("Sample.java"), "class Sample {\n    void run() {\n    }\n}\n");
        List<String> offenders = mutableStaticOffenders(sources, Map.of("Sample.java#gone", "уже не нужно"));

        assertEquals(
                1,
                offenders.size(),
                "Устаревшее исключение обязано валить сторож, иначе список исключений гниёт и скрывает новые нарушения");
        assertEquals(
                true,
                offenders.get(0).contains("устаревшее исключение"),
                "Отчёт об устаревшем исключении должен называть его прямо, а получили: " + offenders);
    }

    private static Map<Path, String> testSources() {
        Map<Path, String> sources = new LinkedHashMap<>(SourceFiles.javaUnder(SourceFiles.TEST));
        SourceFiles.javaUnder(SourceFiles.GAME_TESTS).forEach((file, text) -> {
            if (text.contains("@GameTest") || text.contains("@SceneStep")) {
                sources.put(file, text);
            }
        });
        return sources;
    }

    private static String code(String text) {
        return COMMENT.matcher(STRING_LITERAL
                        .matcher(SourceFiles.withoutTextBlocks(text))
                        .replaceAll("\"\""))
                .replaceAll("");
    }

    private static List<String> mutableStaticOffenders(Map<Path, String> sources) {
        return mutableStaticOffenders(sources, STATIC_EXCEPTIONS);
    }

    private static List<String> mutableStaticOffenders(Map<Path, String> sources, Map<String, String> exceptions) {
        List<String> offenders = new ArrayList<>();
        Map<String, Boolean> used = new LinkedHashMap<>();
        exceptions.keySet().forEach(key -> used.put(key, false));
        sources.forEach((file, text) -> {
            String path = file.toString().replace('\\', '/');
            String body = code(text);
            Matcher field = MUTABLE_STATIC_FIELD.matcher(body);
            while (field.find()) {
                addStaticOffender(offenders, used, path, body, field.start(), field.group(2));
            }
            Matcher collection = MUTABLE_STATIC_COLLECTION.matcher(body);
            while (collection.find()) {
                addStaticOffender(offenders, used, path, body, collection.start(), collection.group(2));
            }
        });
        used.forEach((key, hit) -> {
            if (!hit) {
                offenders.add("устаревшее исключение " + key + " — в коде такого нарушения больше нет, убери из "
                        + "STATIC_EXCEPTIONS");
            }
        });
        return offenders;
    }

    private static void addStaticOffender(
            List<String> offenders, Map<String, Boolean> used, String path, String body, int offset, String field) {
        String key = path + "#" + field;
        for (String exception : used.keySet()) {
            if (key.endsWith("/" + exception) || key.equals(exception)) {
                used.put(exception, true);
                return;
            }
        }
        offenders.add(path + ":" + SourceFiles.lineOf(body, offset) + " изменяемое static-поле " + field);
    }

    private static List<Integer> disabledLines(String text) {
        List<Integer> lines = new ArrayList<>();
        Matcher matcher = DISABLED.matcher(text);
        while (matcher.find()) {
            lines.add(SourceFiles.lineOf(text, matcher.start()));
        }
        return lines;
    }

    private static int reflectionCount(String text) {
        int count = 0;
        Matcher matcher = PRIVATE_REFLECTION.matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static List<String> staticFieldNames(String text) {
        List<String> names = new ArrayList<>();
        Matcher field = MUTABLE_STATIC_FIELD.matcher(text);
        while (field.find()) {
            names.add(field.group(2));
        }
        Matcher collection = MUTABLE_STATIC_COLLECTION.matcher(text);
        while (collection.find()) {
            names.add(collection.group(2));
        }
        return names;
    }
}
