package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class GameTestIsolationGuardTests {
    private static final Pattern GAMETEST_ANNOTATION =
            Pattern.compile("@(?:GameTest|SceneStep)\\s*\\(([^)]*)\\)", Pattern.MULTILINE | Pattern.DOTALL);
    private static final String[] SPAWN_MARKERS = {
        ".spawn(",
        "ModEntities.SPIRIT",
        "mockPlayer",
        "makeMockServerPlayer",
        "spawnSpirit",
        "Scene.spirit(",
        "spawnWith(",
    };
    private static final String[] CLEANUP_MARKERS = {
        "GameTestFixtures.reset",
        "GameTestFixtures.sweepEntities",
        "GameTestFixtures.begin",
        "GameTestFixtures.finish",
        "TacticsScene.prepare",
        "MovementScene.prepare",
    };
    private static final Pattern HELPER_DEFINITION = Pattern.compile(
            "static\\s+[\\w<>,\\[\\] ?.]+\\s+(\\w+)\\s*\\([^)]*GameTestHelper[^)]*\\)[^{]*\\{", Pattern.MULTILINE);

    @Test
    void gameTestSpawn_WithoutStartCleanup_IsReported() {
        List<String> offenders = new ArrayList<>();
        collectOffenders(SourceFiles.javaUnder(SourceFiles.GAME_TESTS), offenders);

        assertEquals(
                List.of(),
                offenders,
                "GameTest со спавном обязан начинать сцену с общей зачистки: фреймворк не чистит мир между "
                        + "пачками (clearOnBatch=false), сущности прошлых сцен живут дальше, а тактика духа видит "
                        + "врагов в радиусе 32 блоков — чужой моб замораживает духа (tactics.wantsControl → "
                        + "brain.suspend) и скрипт стоит. Зачистка шире радиуса восприятия (запас 48) в начале сцены "
                        + "защищает и от предшественников, и от соседей по сетке. Вызови GameTestFixtures.reset, "
                        + "GameTestFixtures.sweepEntities или сцену, которая их вызывает (TacticsScene.prepare, "
                        + "MovementScene.prepare), первой строкой метода — до первого спавна. Правило: todo.md → "
                        + "«Изоляция сцен GameTest». Нарушители");
    }

    @Test
    void sceneAlias_CleanHelperBeforeSpawn_IsAccepted() {
        String sample = """
                        class Sample {
                            private static void arenaFloor(GameTestHelper helper) {
                                GameTestFixtures.reset(helper);
                            }
                            @GameTest(template = "arena", timeoutTicks = 200)
                            public static void till_WithoutHoe_FailsFast(GameTestHelper helper) {
                                arenaFloor(helper);
                                SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
                                helper.succeed();
                            }
                        }
                        """;

        assertEquals(
                List.of(),
                offendersOf(sample),
                "Сторож ложно срабатывает: локальный помощник с зачисткой — законное начало сцены");
    }

    @Test
    void sceneAlias_DirtyHelperBeforeSpawn_IsReported() {
        String sample = """
                        class Sample {
                            private static void arenaFloor(GameTestHelper helper) {
                                GameTestFixtures.buildFloor(helper, Blocks.STONE);
                            }
                            @GameTest(template = "arena", timeoutTicks = 200)
                            public static void till_WithoutHoe_FailsFast(GameTestHelper helper) {
                                arenaFloor(helper);
                                SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
                                helper.succeed();
                            }
                        }
                        """;

        assertEquals(
                List.of("till_WithoutHoe_FailsFast"),
                offendersOf(sample),
                "Сторож ослеп: помощник без зачистки не изолирует сцену, даже если вызван до спавна");
    }

    @Test
    void gameTestSpawnPattern_SpawnWithoutCleanup_IsReported() {
        String sample = """
                        class Sample {
                            @GameTest(template = "arena", timeoutTicks = 200)
                            public static void zombie_NearSpirit_TakesTarget(GameTestHelper helper) {
                                SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
                                helper.succeed();
                            }
                        }
                        """;

        assertEquals(
                List.of("zombie_NearSpirit_TakesTarget"),
                offendersOf(sample),
                "Сторож ослеп: спавн без стартовой зачистки должен попадать в нарушители");
    }

    @Test
    void gameTestSpawnPattern_CleanupAfterSpawn_IsReported() {
        String sample = """
                        class Sample {
                            @GameTest(template = "arena", timeoutTicks = 200)
                            public static void zombie_NearSpirit_TakesTarget(GameTestHelper helper) {
                                SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
                                GameTestFixtures.reset(helper);
                                helper.succeed();
                            }
                        }
                        """;

        assertEquals(
                List.of("zombie_NearSpirit_TakesTarget"),
                offendersOf(sample),
                "Сторож ослеп: зачистка после спавна не защищает от чужих сущностей — она обязана быть до спавна");
    }

    @Test
    void gameTestSpawnPattern_CleanupBeforeSpawn_IsAccepted() {
        String sample = """
                        class Sample {
                            @GameTest(template = "arena", timeoutTicks = 200)
                            public static void zombie_NearSpirit_TakesTarget(GameTestHelper helper) {
                                GameTestFixtures.sweepEntities(helper);
                                SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
                                helper.succeed();
                            }
                        }
                        """;

        assertEquals(
                List.of(), offendersOf(sample), "Сторож ложно срабатывает: зачистка до спавна — законное начало сцены");
    }

    @Test
    void gameTestBody_SecondResetCall_IsReported() {
        String sample = """
                        class Sample {
                            @GameTest(template = "arena", timeoutTicks = 200)
                            public static void fill_TwoCubeBottomUp_SpiritEndsOutside(GameTestHelper helper) {
                                GameTestFixtures.reset(helper);
                                SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
                                helper.succeedWhen(() -> {
                                    GameTestFixtures.reset(helper);
                                });
                            }
                        }
                        """;

        assertEquals(
                List.of("fill_TwoCubeBottomUp_SpiritEndsOutside"),
                finishOffendersOf(sample),
                "Сторож ослеп: поздний reset в лямбде убивает давно бегущих соседей по пачке — там обязан "
                        + "быть узкий finish");
    }

    @Test
    void gameTestBody_ResetAndFinish_IsAccepted() {
        String sample = """
                        class Sample {
                            @GameTest(template = "arena", timeoutTicks = 200)
                            public static void fill_TwoCubeBottomUp_SpiritEndsOutside(GameTestHelper helper) {
                                GameTestFixtures.reset(helper);
                                SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
                                helper.succeedWhen(() -> {
                                    GameTestFixtures.finish(helper);
                                });
                            }
                        }
                        """;

        assertEquals(
                List.of(),
                finishOffendersOf(sample),
                "Сторож ложно срабатывает: стартовый reset плюс узкий finish в лямбде — законная сцена");
    }

    @Test
    void gameTestFinish_NestedResets_UseFinishOnly() {
        List<String> offenders = new ArrayList<>();
        collectFinishOffenders(SourceFiles.javaUnder(SourceFiles.GAME_TESTS), offenders);

        assertEquals(
                List.of(),
                offenders,
                "Поздняя зачистка (внутри лямбды succeedWhen/runAfterDelay) обязана быть узкой "
                        + "GameTestFixtures.finish: широкая убивает давно бегущих соседей по пачке — тесты "
                        + "пачки идут одновременно, что доказал красный прогон after1 (20 падений, 5 тиков 0 "
                        + "за секунду в пачке build_result). Стартовая зачистка остаётся широкой reset. "
                        + "Правило: todo.md → «Изоляция сцен GameTest». Нарушители");
    }

    private static void collectFinishOffenders(Map<Path, String> sources, List<String> offenders) {
        sources.forEach((file, text) -> {
            for (Method method : gameTestMethods(text)) {
                if (countOccurrences(method.body(), "GameTestFixtures.reset(") > 1) {
                    offenders.add(
                            file + ":" + SourceFiles.lineOf(text, method.annotationStart()) + " — " + method.name());
                }
            }
        });
    }

    private static List<String> finishOffendersOf(String text) {
        List<String> names = new ArrayList<>();
        for (Method method : gameTestMethods(text)) {
            if (countOccurrences(method.body(), "GameTestFixtures.reset(") > 1) {
                names.add(method.name());
            }
        }
        return names;
    }

    private static int countOccurrences(String body, String marker) {
        int count = 0;
        int at = 0;
        while ((at = body.indexOf(marker, at)) >= 0) {
            count++;
            at += marker.length();
        }
        return count;
    }

    private static void collectOffenders(Map<Path, String> sources, List<String> offenders) {
        sources.forEach((file, text) -> {
            List<String> cleanHelpers = cleanHelpers(text);
            for (Method method : gameTestMethods(text)) {
                if (spawns(method.body()) && !cleanedBeforeSpawn(method.body(), cleanHelpers)) {
                    offenders.add(
                            file + ":" + SourceFiles.lineOf(text, method.annotationStart()) + " — " + method.name());
                }
            }
        });
    }

    private static List<String> offendersOf(String text) {
        List<String> names = new ArrayList<>();
        List<String> cleanHelpers = cleanHelpers(text);
        for (Method method : gameTestMethods(text)) {
            if (spawns(method.body()) && !cleanedBeforeSpawn(method.body(), cleanHelpers)) {
                names.add(method.name());
            }
        }
        return names;
    }

    private static List<Method> gameTestMethods(String text) {
        List<Method> methods = new ArrayList<>();
        Matcher matcher = GAMETEST_ANNOTATION.matcher(text);
        while (matcher.find()) {
            int nameStart = text.indexOf('(', text.indexOf("void", matcher.end()));
            if (nameStart < 0) {
                continue;
            }
            int nameEnd = nameStart;
            while (nameEnd > 0 && Character.isJavaIdentifierPart(text.charAt(nameEnd - 1))) {
                nameEnd--;
            }
            String name = text.substring(nameEnd, nameStart);
            int bodyStart = text.indexOf('{', nameStart);
            int bodyEnd = matchingBrace(text, bodyStart);
            if (bodyStart < 0 || bodyEnd < 0) {
                continue;
            }
            methods.add(new Method(name, matcher.start(), text.substring(bodyStart, bodyEnd + 1)));
        }
        return methods;
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

    private static List<String> cleanHelpers(String text) {
        List<String> clean = new ArrayList<>();
        Matcher matcher = HELPER_DEFINITION.matcher(text);
        while (matcher.find()) {
            int bodyStart = text.indexOf('{', matcher.end() - 1);
            int bodyEnd = bodyStart < 0 ? -1 : matchingBrace(text, bodyStart);
            if (bodyStart < 0 || bodyEnd < 0) {
                continue;
            }
            String body = text.substring(bodyStart, bodyEnd + 1);
            if (firstIndexOf(body, CLEANUP_MARKERS) < Integer.MAX_VALUE) {
                clean.add(matcher.group(1));
            }
        }
        return clean;
    }

    private static boolean spawns(String body) {
        for (String marker : SPAWN_MARKERS) {
            if (body.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static boolean cleanedBeforeSpawn(String body, List<String> cleanHelpers) {
        int firstCleanup = firstIndexOf(body, CLEANUP_MARKERS);
        for (String helper : cleanHelpers) {
            int at = body.indexOf(helper + "(");
            if (at >= 0) {
                firstCleanup = Math.min(firstCleanup, at);
            }
        }
        return firstCleanup < firstIndexOf(body, SPAWN_MARKERS);
    }

    private static int firstIndexOf(String body, String[] markers) {
        int first = Integer.MAX_VALUE;
        for (String marker : markers) {
            int at = body.indexOf(marker);
            if (at >= 0) {
                first = Math.min(first, at);
            }
        }
        return first;
    }

    private record Method(String name, int annotationStart, String body) {}
}
