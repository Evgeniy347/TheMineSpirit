package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Сторож правила проекта: GameTest, который спавнит духа или ставит блоки, не должен использовать шаблон
 * {@code "empty"} (площадка 3×3×3). Сцена такого теста вылезает за площадку в соседний неактивный чанк, и
 * дух/блок не делает первого тика — отсюда флеки примерно 1 из 8. Для таких тестов нужен шаблон
 * {@code "arena"} (11×8×11), где сцена целиком помещается на активной площадке.
 */
class GameTestTemplateGuardTests {
    private static final Pattern GAMETEST_ANNOTATION = Pattern.compile(
            "@GameTest\\s*\\(([^)]*)\\)", Pattern.MULTILINE | Pattern.DOTALL);
    private static final Pattern EMPTY_TEMPLATE =
            Pattern.compile("template\\s*=\\s*(?:\"empty\"|EMPTY_TEMPLATE)(?![A-Za-z0-9_])");
    private static final String[] SPAWN_OR_BLOCK_MARKERS = {
            "ModEntities.SPIRIT",
            "spawnSpirit",
            ".spawn(",
            "setBlock",
            "buildFloor",
            ".fill(",
    };

    @Test
    void gameTestTemplate_SpiritSpawnOrBlockPlacementOnEmpty_IsReported() {
        List<String> offenders = new ArrayList<>();
        collectOffenders(SourceFiles.javaUnder(SourceFiles.GAME_TESTS), offenders);

        assertEquals(List.of(), offenders,
                "GameTest, который спавнит духа или ставит блоки, не должен использовать шаблон \"empty\" (3×3×3): "
                        + "сцена вылезает в соседний неактивный чанк, дух/блок не делает первого тика и тест флекает "
                        + "примерно 1 из 8. Такому тесту нужен шаблон \"arena\" (11×8×11), и вся сцена — внутри "
                        + "площадки. Правило: docs/TESTING.md → «Два уровня». Нарушители");
    }

    @Test
    void gameTestTemplatePattern_EmptyWithSpawn_FindsViolation() {
        String sample = "class Sample {\n"
                + "    @GameTest(template = \"empty\", timeoutTicks = 100)\n"
                + "    public static void spawn_SpiritType_Lives(GameTestHelper helper) {\n"
                + "        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));\n"
                + "        helper.succeed();\n"
                + "    }\n"
                + "}\n";

        assertEquals(List.of("spawn_SpiritType_Lives"), offendersOf(sample),
                "Сторож ослеп: спавн духа в шаблоне \"empty\" должен попадать в нарушители");
    }

    @Test
    void gameTestTemplatePattern_EmptyWithSetBlock_FindsViolation() {
        String sample = "class Sample {\n"
                + "    @GameTest(template = \"empty\", timeoutTicks = 100)\n"
                + "    public static void floor_Stone_IsBuilt(GameTestHelper helper) {\n"
                + "        helper.setBlock(0, 0, 0, Blocks.STONE);\n"
                + "        helper.succeed();\n"
                + "    }\n"
                + "}\n";

        assertEquals(List.of("floor_Stone_IsBuilt"), offendersOf(sample),
                "Сторож ослеп: постановка блока в шаблоне \"empty\" должна попадать в нарушители");
    }

    @Test
    void gameTestTemplatePattern_ArenaWithSpawn_IsClean() {
        String sample = "class Sample {\n"
                + "    @GameTest(template = \"arena\", batch = \"spawn\", timeoutTicks = 100)\n"
                + "    public static void spawn_SpiritType_Lives(GameTestHelper helper) {\n"
                + "        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));\n"
                + "        helper.succeed();\n"
                + "    }\n"
                + "}\n";

        assertEquals(List.of(), offendersOf(sample),
                "Сторож ложно срабатывает: шаблон \"arena\" со сценой внутри площадки — законное решение");
    }

    @Test
    void gameTestTemplatePattern_EmptyWithoutSpawnOrBlocks_IsClean() {
        String sample = "class Sample {\n"
                + "    @GameTest(template = \"empty\", timeoutTicks = 100)\n"
                + "    public static void registry_EveryScript_IsDescribed(GameTestHelper helper) {\n"
                + "        helper.assertTrue(SpiritScripts.registryFor(null).ids().size() > 0, \"реестр пуст\");\n"
                + "        helper.succeed();\n"
                + "    }\n"
                + "}\n";

        assertEquals(List.of(), offendersOf(sample),
                "Сторож ложно срабатывает: \"empty\" без спавна и блоков безопасен — читать нечего");
    }

    private static void collectOffenders(Map<Path, String> sources, List<String> offenders) {
        sources.forEach((file, text) -> {
            for (Method method : methodsOnEmptyTemplate(text)) {
                if (spawnsOrPlacesBlocks(method.body())) {
                    offenders.add(file + ":" + SourceFiles.lineOf(text, method.annotationStart())
                            + " — " + method.name());
                }
            }
        });
    }

    private static List<String> offendersOf(String text) {
        List<String> names = new ArrayList<>();
        for (Method method : methodsOnEmptyTemplate(text)) {
            if (spawnsOrPlacesBlocks(method.body())) {
                names.add(method.name());
            }
        }
        return names;
    }

    private static List<Method> methodsOnEmptyTemplate(String text) {
        List<Method> methods = new ArrayList<>();
        Matcher matcher = GAMETEST_ANNOTATION.matcher(text);
        while (matcher.find()) {
            if (!EMPTY_TEMPLATE.matcher(matcher.group(1)).find()) {
                continue;
            }
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

    private static boolean spawnsOrPlacesBlocks(String body) {
        for (String marker : SPAWN_OR_BLOCK_MARKERS) {
            if (body.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private record Method(String name, int annotationStart, String body) {
    }
}