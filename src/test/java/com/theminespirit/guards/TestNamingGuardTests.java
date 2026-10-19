package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TestNamingGuardTests {
    private static final Pattern TEST_METHOD =
            Pattern.compile("@(Test|Property|Example|GameTest|ParameterizedTest)\\b[^\\n]*\\n"
                    + "(?:[ \\t]*@[^\\n]*\\n)*"
                    + "[ \\t]*(?:(?:public|protected|private|static|final)\\s+)*void\\s+(\\w+)\\s*\\(");
    private static final Pattern GOOD_NAME = Pattern.compile("[a-z][A-Za-z0-9]*_[A-Z][A-Za-z0-9]*_[A-Z][A-Za-z0-9]*");

    @Test
    void testMethods_AllTestSources_FollowSubjectConditionExpectedPattern() {
        List<String> offenders = new ArrayList<>();
        collectBadNames(SourceFiles.javaUnder(SourceFiles.TEST), offenders);
        collectBadNames(SourceFiles.javaUnder(SourceFiles.GAME_TESTS), offenders);

        assertEquals(
                List.of(),
                offenders,
                "Имя теста — это отчёт при красном прогоне: по нему без чтения кода видно, ЧТО проверяли, "
                        + "ПРИ КАКОМ условии и КАКОЙ результат ждали. Имя из двух частей или условие с маленькой "
                        + "буквы этого не говорит. Формат: subject_Condition_ExpectedResult, три части через '_', "
                        + "вторая и третья — с большой буквы. Пример: defaultRadius_WithinLimits → "
                        + "defaultRadius_DefaultValue_LiesWithinMinMax. Правило: docs/TESTING.md → «Имя теста». "
                        + "Нарушители");
    }

    @Test
    void testMethodPattern_TwoPartName_IsReported() {
        String sample = "class Sample {\n    @Test\n    void defaultRadius_WithinLimits() {\n    }\n}\n";

        assertEquals(
                List.of("defaultRadius_WithinLimits"),
                badNames(sample),
                "Сторож ослеп: имя из двух частей должно попадать в нарушители");
    }

    @Test
    void testMethodPattern_AnnotationWithArgumentsAndParameters_IsFound() {
        String sample = "    @GameTest(template = \"empty\", timeoutTicks = 100)\n"
                + "    @SuppressWarnings(\"unused\")\n"
                + "    public static void spawn_spiritType_Lives(GameTestHelper helper) {\n";

        assertEquals(
                List.of("spawn_spiritType_Lives"),
                badNames(sample),
                "Сторож ослеп: тест с аргументами аннотации, второй аннотацией и параметрами должен находиться, "
                        + "а условие с маленькой буквы — считаться нарушением");
    }

    @Test
    void testMethodPattern_WellNamedTest_IsAccepted() {
        String sample = "    @Property\n    void hash_InventoryOrderChanged_StaysEqual(@ForAll int seed) {\n";

        assertEquals(
                List.of(),
                badNames(sample),
                "Сторож ложно срабатывает: имя subject_Condition_Expected должно приниматься");
    }

    private static void collectBadNames(Map<Path, String> sources, List<String> offenders) {
        sources.forEach((file, text) -> {
            Matcher matcher = TEST_METHOD.matcher(text);
            while (matcher.find()) {
                String name = matcher.group(2);
                if (!GOOD_NAME.matcher(name).matches()) {
                    offenders.add(file + ":" + SourceFiles.lineOf(text, matcher.start(2)) + " " + name);
                }
            }
        });
    }

    private static List<String> badNames(String text) {
        List<String> names = new ArrayList<>();
        Matcher matcher = TEST_METHOD.matcher(text);
        while (matcher.find()) {
            if (!GOOD_NAME.matcher(matcher.group(2)).matches()) {
                names.add(matcher.group(2));
            }
        }
        return names;
    }
}
