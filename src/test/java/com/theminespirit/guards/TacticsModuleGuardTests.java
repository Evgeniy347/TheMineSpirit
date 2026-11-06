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

class TacticsModuleGuardTests {
    private static final Path MAIN = Path.of("tactics/src/main/java");
    private static final Path TEST = Path.of("tactics/src/test/java");
    private static final Path PACKAGE = Path.of("com/theminespirit");
    private static final List<String> OWN_PACKAGES =
            List.of("core.tactics", "core.intellect", "core.coordinator", "core.planner", "core.processing");
    private static final List<String> MODULE_PACKAGES = List.of("core.policy", "core.inventory", "core.night");
    private static final List<String> TEST_MODULE_PACKAGES =
            List.of("core.board", "core.forestry", "core.reload", "core.script");

    @Test
    void tacticsSources_LiveInTacticsModule_NotInRoot() {
        assertTrue(
                Files.isDirectory(MAIN.resolve(PACKAGE).resolve("core/tactics")),
                "Тактика не собрана отдельным модулем: tactics/src/main/java/com/theminespirit/core/tactics "
                        + "отсутствует. Как надо: исходники тактики физически в :tactics, пакет прежний.");
        List<String> misplaced = new ArrayList<>();
        for (String logical : List.of(
                "core/tactics/Orders.java",
                "core/tactics/TacticsEngine.java",
                "core/tactics/adapter/TacticsSchedule.java",
                "core/intellect/IntellectTuning.java",
                "core/coordinator/CoordinatorState.java",
                "core/planner/Goal.java",
                "core/processing/ProcessingStock.java")) {
            Path file = SourceFiles.path(logical);
            if (!file.normalize().startsWith(MAIN)) {
                misplaced.add(logical + " → " + file);
            }
        }
        SourceFiles.allMain().forEach((file, text) -> {
            String logical = SourceFiles.logical(file);
            for (String own : OWN_PACKAGES) {
                if (logical.startsWith(own.replace('.', '/') + "/")
                        && !file.normalize().startsWith(MAIN)
                        && !file.normalize().startsWith(SourceFiles.API)) {
                    misplaced.add("вне :tactics: " + logical);
                }
            }
        });
        assertEquals(
                List.of(),
                misplaced,
                "Классы тактики обязаны лежать в :tactics (кроме контрактов :api): иначе правка тактики снова "
                        + "гоняет все тесты корня, а модуль не собирается сам. Нарушители:");
    }

    @Test
    void tacticsTests_LiveInTacticsModule_LocalTestsExist() {
        assertTrue(
                Files.isDirectory(TEST.resolve(PACKAGE).resolve("core/tactics")),
                "У :tactics нет своих тестов (tactics/src/test/java): перенос тестов молча отключил проверки "
                        + "тактики.");
        assertTrue(
                Files.isRegularFile(TEST.resolve(PACKAGE).resolve("core/tactics/TacticsEngineTests.java")),
                "Тест TacticsEngineTests обязан переехать в :tactics: иначе ./gradlew :tactics:test проверяет не "
                        + "тактику.");
        List<String> misplaced = new ArrayList<>();
        SourceFiles.allTests().forEach((file, text) -> {
            String logical = SourceFiles.logical(file);
            for (String own : OWN_PACKAGES) {
                if (logical.startsWith(own.replace('.', '/') + "/")
                        && !file.normalize().startsWith(TEST)
                        && !file.normalize().startsWith(SourceFiles.API_TEST)) {
                    misplaced.add("вне :tactics: " + logical);
                }
            }
        });
        assertEquals(
                List.of(),
                misplaced,
                "Тесты тактики обязаны лежать в :tactics: иначе :tactics:test не проверяет модуль, а корень "
                        + "гоняет чужие тесты на каждую правку. Нарушители:");
    }

    @Test
    void tacticsModule_Imports_OnlyApiAndDeclaredModules() {
        Set<String> apiClasses = apiClasses(SourceFiles.javaUnder(SourceFiles.apiPackage()));
        List<String> visible = new ArrayList<>(OWN_PACKAGES);
        visible.addAll(MODULE_PACKAGES);
        assertEquals(
                List.of(),
                violations(SourceFiles.javaUnder(MAIN.resolve(PACKAGE)), apiClasses, visible),
                "Тактика зависит только от :api, :policy, :mech:inventory, :mech:small, объявленных в "
                        + "build.gradle: импорт корня (reload/script) возвращает цикл и запрещает отдельную сборку "
                        + ":tactics. Как надо: контракт — в :api, данные подставляет корень. Нарушители:");
    }

    @Test
    void tacticsTests_Imports_OnlyApiAndDeclaredModules() {
        Set<String> apiClasses = apiClasses(SourceFiles.javaUnder(SourceFiles.apiPackage()));
        List<String> visible = new ArrayList<>(OWN_PACKAGES);
        visible.addAll(MODULE_PACKAGES);
        visible.addAll(TEST_MODULE_PACKAGES);
        assertEquals(
                List.of(),
                violations(SourceFiles.javaUnder(TEST.resolve(PACKAGE)), apiClasses, visible),
                "Тест тактики компилируется только с :api, своими и объявленными модулями: импорт корня в тесте "
                        + "скрывает, что :tactics без корня не собрать. Нарушители:");
    }
}
