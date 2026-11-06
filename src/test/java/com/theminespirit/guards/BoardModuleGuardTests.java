package com.theminespirit.guards;

import static com.theminespirit.guards.MechanicsModuleImports.apiClasses;
import static com.theminespirit.guards.MechanicsModuleImports.violations;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BoardModuleGuardTests {
    private static final Path MAIN = Path.of("board/src/main/java");
    private static final Path TEST = Path.of("board/src/test/java");
    private static final Path PACKAGE = Path.of("com/theminespirit");
    private static final List<String> OWN_PACKAGES = List.of("core.board", "core.board.graph");
    private static final List<String> MECHANIC_PACKAGES = List.of(
            "core.building",
            "core.town",
            "core.movement",
            "core.inventory",
            "core.crafting",
            "core.alchemy",
            "core.armor",
            "core.doors",
            "core.experience",
            "core.hunger",
            "core.night",
            "core.repair",
            "core.sleep",
            "core.social");
    private static final List<String> TEST_MECHANIC_PACKAGES = List.of("core.forestry");

    @Test
    void boardSources_LiveInBoardModule_NotInRoot() {
        assertTrue(
                Files.isDirectory(MAIN.resolve(PACKAGE).resolve("core/board")),
                "Доска не собрана отдельным модулем: board/src/main/java/com/theminespirit/core/board "
                        + "отсутствует. Как надо: исходники доски физически в :board, пакет прежний.");
        List<String> misplaced = new ArrayList<>();
        for (String logical : List.of(
                "core/board/Board.java",
                "core/board/BoardTicket.java",
                "core/board/ToolGate.java",
                "core/board/BoardTuning.java",
                "core/board/WorkStand.java",
                "core/board/graph/GraphPlanner.java")) {
            Path file = SourceFiles.path(logical);
            if (!file.normalize().startsWith(MAIN)) {
                misplaced.add(logical + " → " + file);
            }
        }
        SourceFiles.allMain().forEach((file, text) -> {
            String logical = SourceFiles.logical(file);
            if (logical.startsWith("core/board/")
                    && !file.normalize().startsWith(MAIN)
                    && !file.normalize().startsWith(SourceFiles.API)) {
                misplaced.add("вне :board: " + logical);
            }
        });
        assertEquals(
                List.of(),
                misplaced,
                "Классы доски обязаны лежать в :board (кроме контрактов :api): иначе правка доски снова гоняет "
                        + "все тесты корня, а модуль не собирается сам. Нарушители:");
    }

    @Test
    void boardTests_LiveInBoardModule_LocalTestsExist() {
        assertTrue(
                Files.isDirectory(TEST.resolve(PACKAGE).resolve("core/board")),
                "У :board нет своих тестов (board/src/test/java): перенос тестов молча отключил проверки доски.");
        assertTrue(
                Files.isRegularFile(TEST.resolve(PACKAGE).resolve("core/board/BoardRequireTests.java")),
                "Тест BoardRequireTests обязан переехать в :board: иначе ./gradlew :board:test проверяет не доску.");
    }

    @Test
    void boardModule_Imports_OnlyApiAndDeclaredMechanics() {
        Set<String> apiClasses = apiClasses(SourceFiles.javaUnder(SourceFiles.apiPackage()));
        List<String> visible = new ArrayList<>(OWN_PACKAGES);
        visible.addAll(MECHANIC_PACKAGES);
        assertEquals(
                List.of(),
                violations(SourceFiles.javaUnder(MAIN.resolve(PACKAGE)), apiClasses, visible),
                "Доска зависит только от :api и механик, которые сама объявила в build.gradle: импорт корня "
                        + "(script/reload/neoforge) возвращает цикл и запрещает отдельную сборку :board. Как надо: "
                        + "контракт — в :api, данные и действие — у владельца. Нарушители:");
    }

    @Test
    void boardTests_Imports_OnlyApiAndDeclaredMechanics() {
        Set<String> apiClasses = apiClasses(SourceFiles.javaUnder(SourceFiles.apiPackage()));
        List<String> visible = new ArrayList<>(OWN_PACKAGES);
        visible.addAll(MECHANIC_PACKAGES);
        visible.addAll(TEST_MECHANIC_PACKAGES);
        visible.add("testing");
        assertEquals(
                List.of(),
                violations(SourceFiles.javaUnder(TEST.resolve(PACKAGE)), apiClasses, visible),
                "Тест доски компилируется только с :api, своими и объявленными модулями: импорт корня в тесте "
                        + "скрывает, что :board без корня не собрать. Нарушители:");
    }
}
