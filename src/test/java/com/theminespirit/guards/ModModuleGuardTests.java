package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModModuleGuardTests {
    private static final Path MAIN = Path.of("mod/src/main/java");
    private static final Path TEST = Path.of("mod/src/test/java");
    private static final Path PACKAGE = Path.of("com/theminespirit");
    private static final Path ROOT_MAIN = Path.of("src/main/java");

    @Test
    void neoforgeSources_LiveInModModule_NotInRoot() {
        assertTrue(
                Files.isDirectory(MAIN.resolve(PACKAGE).resolve("neoforge")),
                "Склейка NeoForge не вынесена в модуль :mod: mod/src/main/java/com/theminespirit/neoforge "
                        + "отсутствует. Как надо: реестры, сущности, события, команды, HTTP и клиент физически "
                        + "в :mod, пакет прежний.");
        assertTrue(
                Files.isRegularFile(MAIN.resolve(PACKAGE).resolve("TheMineSpirit.java")),
                "Точка входа мода TheMineSpirit.java обязана лежать в :mod: иначе jar собирается из корня и "
                        + "корень снова становится складом.");
        assertTrue(
                !Files.isDirectory(ROOT_MAIN) || javaFiles(ROOT_MAIN).isEmpty(),
                "Корневой src/main/java обязан остаться пустым: найденные классы — "
                        + javaFiles(ROOT_MAIN)
                        + ". Как надо: производственный код в модулях (:mod, :gametest, :mech:*), корень — "
                        + "только сторожа и агрегаторы.");
    }

    @Test
    void modResources_Placement_LiveInModModule() {
        for (String file : List.of(
                "data/theminespirit/board/graph.json",
                "data/theminespirit/board/graph-recipes.json",
                "data/theminespirit/schematics/hut_1.txt")) {
            assertTrue(
                    Files.isRegularFile(MAIN.resolveSibling("resources").resolve(file)),
                    "Ресурс " + file + " обязан лежать в mod/src/main/resources: jar из :mod без него теряет "
                            + "данные доски и схемы.");
        }
        assertTrue(
                Files.isRegularFile(MAIN.resolveSibling("templates").resolve("META-INF/neoforge.mods.toml")),
                "Шаблон neoforge.mods.toml обязан лежать в mod/src/main/templates: без него generateModMetadata "
                        + "не соберёт метаданные мода.");
        assertTrue(
                Files.isRegularFile(MAIN.resolveSibling("resources").resolve("META-INF/accesstransformer.cfg")),
                "accesstransformer.cfg обязан лежать в :mod: без него сцены и мод не компилируются.");
    }

    @Test
    void modTests_Placement_LiveInModModule() {
        assertTrue(
                Files.isDirectory(TEST.resolve(PACKAGE).resolve("neoforge")),
                "У :mod нет своих тестов (mod/src/test/java/com/theminespirit/neoforge): перенос тестов молча "
                        + "отключил проверки адаптеров мода.");
        assertTrue(
                Files.isRegularFile(TEST.resolve(PACKAGE).resolve("neoforge/SchematicResourcesTests.java")),
                "Тест SchematicResourcesTests обязан переехать в :mod: иначе :mod:test проверяет не мод.");
        List<String> misplaced = new ArrayList<>();
        SourceFiles.allTests().forEach((file, text) -> {
            String logical = SourceFiles.logical(file);
            if (logical.startsWith("neoforge/") && !file.normalize().startsWith(TEST)) {
                misplaced.add("вне :mod: " + logical);
            }
        });
        assertEquals(
                List.of(),
                misplaced,
                "Тесты склейки мода обязаны лежать в :mod: иначе корень гоняет чужие тесты на каждую правку. "
                        + "Нарушители:");
    }

    @Test
    void modSources_Dependencies_DoNotKnowGameTestModule() {
        List<String> violations = new ArrayList<>();
        SourceFiles.javaUnder(MAIN.resolve(PACKAGE)).forEach((file, text) -> {
            if (text.contains("com.theminespirit.gametest.") || text.contains("package com.theminespirit.gametest")) {
                violations.add(SourceFiles.logical(file));
            }
        });
        assertEquals(
                List.of(),
                violations,
                "Модуль :mod не знает про :gametest: сцены зависят от мода, а не наоборот, иначе появляется "
                        + "цикл и :mod не собрать отдельно. Нарушители:");
    }

    private static List<String> javaFiles(Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        return SourceFiles.javaUnder(root).keySet().stream()
                .map(Path::toString)
                .map(path -> path.replace('\\', '/'))
                .toList();
    }
}
