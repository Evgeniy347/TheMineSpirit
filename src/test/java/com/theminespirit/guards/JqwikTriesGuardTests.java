package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class JqwikTriesGuardTests {
    private static final Pattern PROPERTY_WITH_TRIES = Pattern.compile(
            "((?:[ \\t]*@[^\\n]*\\n)*)[ \\t]*@Property\\s*\\([^)]*tries\\s*=\\s*(\\d+)[^)]*\\)[^\\n]*\\n((?:[ \\t]*@[^\\n]*\\n)*)",
            Pattern.MULTILINE);
    private static final Pattern JQWIK_SLOW_TAG = Pattern.compile("@Tag\\s*\\(\\s*\"slow\"\\s*\\)");
    private static final int FAST_TIER_MAX_TRIES = 200;

    @Test
    void propertyTries_Over200_WithoutSlowTag() {
        List<String> offenders = new ArrayList<>();
        collectOffenders(SourceFiles.javaUnder(SourceFiles.TEST), offenders);

        assertEquals(
                List.of(),
                offenders,
                "Свойство с tries > 200 обязано нести jqwik-тег @Tag(\"slow\") (net.jqwik.api.Tag): "
                        + "иначе оно гоняется быстрым тиром с заявленной глубиной и ломает бюджет. "
                        + "Правило: docs/TESTING.md → «Property-based тесты». Нарушители");
    }

    @Test
    void propertyTriesPattern_Tries1000_NoSlowTag() {
        String sample = """
                        class Sample {
                            @Property(tries = 1000)
                            void hash_AnySeed_StaysEqual(@ForAll int seed) {
                            }
                        }
                        """;

        assertEquals(
                List.of("tries=1000"),
                triesOf(sample),
                "Сторож ослеп: tries = 1000 без @Tag(\"slow\") должно попадать в нарушители");
    }

    @Test
    void propertyTriesPattern_Tries1000_WithSlowTag() {
        String sample = """
                        import net.jqwik.api.Tag;
                        class Sample {
                            @Property(tries = 1000)
                            @Tag("slow")
                            void hash_AnySeed_StaysEqual(@ForAll int seed) {
                            }
                        }
                        """;

        assertEquals(
                List.of(),
                triesOf(sample),
                "Сторож ложно срабатывает: tries = 1000 с @Tag(\"slow\") — законное исключение");
    }

    @Test
    void propertyTriesPattern_Tries200_NoSlowTag() {
        String sample = """
                        class Sample {
                            @Property
                            void hash_AnySeed_StaysEqual(@ForAll int seed) {
                            }
                        }
                        """;

        assertEquals(
                List.of(),
                triesOf(sample),
                "Сторож ложно срабатывает: tries = 200 — граница быстрого тира, тег не нужен");
    }

    @Test
    void propertyTriesPattern_TriesNotFirstArgument_IsReported() {
        String sample = """
                        class Sample {
                            @Property(seed = "abc", tries = 1000)
                            void hash_AnySeed_StaysEqual(@ForAll int seed) {
                            }
                        }
                        """;

        assertEquals(
                List.of("tries=1000"),
                triesOf(sample),
                "Сторож ослеп: tries = 1000 не первым аргументом должно попадать в нарушители");
    }

    private static void collectOffenders(Map<Path, String> sources, List<String> offenders) {
        sources.forEach((file, text) -> {
            if (file.getFileName().toString().contains("Guard")) {
                return;
            }
            Matcher matcher = PROPERTY_WITH_TRIES.matcher(text);
            while (matcher.find()) {
                int tries = Integer.parseInt(matcher.group(2));
                if (tries > FAST_TIER_MAX_TRIES && !hasSlowTag(matcher)) {
                    offenders.add(file + ":" + SourceFiles.lineOf(text, matcher.start()) + " tries=" + tries);
                }
            }
        });
    }

    private static boolean hasSlowTag(Matcher matcher) {
        return JQWIK_SLOW_TAG.matcher(matcher.group(1)).find()
                || JQWIK_SLOW_TAG.matcher(matcher.group(3)).find();
    }

    private static List<String> triesOf(String text) {
        List<String> names = new ArrayList<>();
        Matcher matcher = PROPERTY_WITH_TRIES.matcher(text);
        while (matcher.find()) {
            int tries = Integer.parseInt(matcher.group(2));
            if (tries > FAST_TIER_MAX_TRIES && !hasSlowTag(matcher)) {
                names.add("tries=" + tries);
            }
        }
        return names;
    }
}
