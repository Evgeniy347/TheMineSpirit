package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GameTestModuleGuardTests {
    private static final Path MAIN = Path.of("gametest/src/main/java");
    private static final Path TEST = Path.of("gametest/src/test/java");
    private static final Path PACKAGE = Path.of("com/theminespirit");
    private static final Path ROOT_MAIN = Path.of("src/main/java");

    @Test
    void gameTestScenes_LiveInGameTestModule_NotInRoot() {
        assertTrue(
                Files.isDirectory(MAIN.resolve(PACKAGE).resolve("gametest")),
                "GameTest-сцены не вынесены в :gametest: gametest/src/main/java/com/theminespirit/gametest "
                        + "отсутствует. Как надо: сцены физически в :gametest, пакет прежний.");
        List<String> misplaced = new ArrayList<>();
        SourceFiles.allMain().forEach((file, text) -> {
            String logical = SourceFiles.logical(file);
            if (logical.startsWith("gametest/") && !file.normalize().startsWith(MAIN)) {
                misplaced.add("вне :gametest: " + logical);
            }
        });
        assertEquals(
                List.of(),
                misplaced,
                "GameTest-сцены обязаны лежать в :gametest: иначе правка сцены гоняет все тесты корня, а "
                        + "runGameTestServer собирает не тот модуль. Нарушители:");
    }

    @Test
    void gameTestModule_Layout_HasOwnRunAndTests() {
        assertTrue(
                Files.isDirectory(TEST.resolve(PACKAGE).resolve("gametest")),
                "У :gametest нет своих тестов (gametest/src/test/java): перенос отключил проверки сцен.");
        assertTrue(
                Files.isRegularFile(TEST.resolve(PACKAGE).resolve("gametest/GameTestWorldSeedTests.java")),
                "Тест GameTestWorldSeedTests обязан переехать в :gametest.");
        assertTrue(
                !Files.isDirectory(ROOT_MAIN)
                        || SourceFiles.javaUnder(ROOT_MAIN).isEmpty(),
                "Корневой src/main/java обязан остаться пустым после переноса сцен и мода.");
    }

    @Test
    void gameTestModule_KnowsModAndMechanics_NotGuards() {
        List<String> violations = new ArrayList<>();
        SourceFiles.javaUnder(MAIN.resolve(PACKAGE)).forEach((file, text) -> {
            if (text.contains("package com.theminespirit.guards") || text.contains("com.theminespirit.testing.")) {
                violations.add(SourceFiles.logical(file));
            }
        });
        assertEquals(
                List.of(),
                violations,
                "Сцены :gametest не тянут сторожа и тестовую обвязку корня: модуль собирается от :mod и "
                        + "механик, а не от тестов корня. Нарушители:");
    }
}
