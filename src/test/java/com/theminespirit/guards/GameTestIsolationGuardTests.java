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
    private static final Pattern GAMETEST_ANNOTATION = Pattern.compile(
            "@GameTest\\s*\\(([^)]*)\\)", Pattern.MULTILINE | Pattern.DOTALL);
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
            "static\\s+[\\w<>,\\[\\] ?.]+\\s+(\\w+)\\s*\\([^)]*GameTestHelper[^)]*\\)[^{]*\\{",
            Pattern.MULTILINE);

    @Test
    void gameTestSpawn_WithoutStartCleanup_IsReported() {
        List<String> offenders = new ArrayList<>();
        collectOffenders(SourceFiles.javaUnder(SourceFiles.GAME_TESTS), offenders);

        assertEquals(List.of(), offenders,
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
        String sample = "class Sample {\n"
                + "    private static void arenaFloor(GameTestHelper helper) {\n"
                + "        GameTestFixtures.reset(helper);\n"
                + "    }\n"
                + "    @GameTest(template = \"arena\", timeoutTicks = 200)\n"
                + "    public static void till_WithoutHoe_FailsFast(GameTestHelper helper) {\n"
                + "        arenaFloor(helper);\n"
                + "        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));\n"
                + "        helper.succeed();\n"
                + "    }\n"
                + "}\n";

        assertEquals(List.of(), offendersOf(sample),
                "Сторож ложно срабатывает: локальный помощник с зачисткой — законное начало сцены");
    }

    @Test
    void sceneAlias_DirtyHelperBeforeSpawn_IsReported() {
        String sample = "class Sample {\n"
                + "    private static void arenaFloor(GameTestHelper helper) {\n"
                + "        GameTestFixtures.buildFloor(helper, Blocks.STONE);\n"
                + "    }\n"
                + "    @GameTest(template = \"arena\", timeoutTicks = 200)\n"
                + "    public static void till_WithoutHoe_FailsFast(GameTestHelper helper) {\n"
                + "        arenaFloor(helper);\n"
                + "        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));\n"
                + "        helper.succeed();\n"
                + "    }\n"
                + "}\n";

        assertEquals(List.of("till_WithoutHoe_FailsFast"), offendersOf(sample),
                "Сторож ослеп: помощник без зачистки не изолирует сцену, даже если вызван до спавна");
    }

    @Test
    void gameTestSpawnPattern_SpawnWithoutCleanup_IsReported() {
        String sample = "class Sample {\n"
                + "    @GameTest(template = \"arena\", timeoutTicks = 200)\n"
                + "    public static void zombie_NearSpirit_TakesTarget(GameTestHelper helper) {\n"
                + "        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));\n"
                + "        helper.succeed();\n"
                + "    }\n"
                + "}\n";

        assertEquals(List.of("zombie_NearSpirit_TakesTarget"), offendersOf(sample),
                "Сторож ослеп: спавн без стартовой зачистки должен попадать в нарушители");
    }

    @Test
    void gameTestSpawnPattern_CleanupAfterSpawn_IsReported() {
        String sample = "class Sample {\n"
                + "    @GameTest(template = \"arena\", timeoutTicks = 200)\n"
                + "    public static void zombie_NearSpirit_TakesTarget(GameTestHelper helper) {\n"
                + "        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));\n"
                + "        GameTestFixtures.reset(helper);\n"
                + "        helper.succeed();\n"
                + "    }\n"
                + "}\n";

        assertEquals(List.of("zombie_NearSpirit_TakesTarget"), offendersOf(sample),
                "Сторож ослеп: зачистка после спавна не защищает от чужих сущностей — она обязана быть до спавна");
    }

    @Test
    void gameTestSpawnPattern_CleanupBeforeSpawn_IsAccepted() {
        String sample = "class Sample {\n"
                + "    @GameTest(template = \"arena\", timeoutTicks = 200)\n"
                + "    public static void zombie_NearSpirit_TakesTarget(GameTestHelper helper) {\n"
                + "        GameTestFixtures.sweepEntities(helper);\n"
                + "        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));\n"
                + "        helper.succeed();\n"
                + "    }\n"
                + "}\n";

        assertEquals(List.of(), offendersOf(sample),
                "Сторож ложно срабатывает: зачистка до спавна — законное начало сцены");
    }

    private static void collectOffenders(Map<Path, String> sources, List<String> offenders) {
        sources.forEach((file, text) -> {
            List<String> cleanHelpers = cleanHelpers(text);
            for (Method method : gameTestMethods(text)) {
                if (spawns(method.body()) && !cleanedBeforeSpawn(method.body(), cleanHelpers)) {
                    offenders.add(file + ":" + SourceFiles.lineOf(text, method.annotationStart())
                            + " — " + method.name());
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

    private record Method(String name, int annotationStart, String body) {
    }
}
