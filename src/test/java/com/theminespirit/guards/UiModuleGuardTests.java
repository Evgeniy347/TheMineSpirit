package com.theminespirit.guards;

import static com.theminespirit.guards.MechanicsModuleImports.apiClasses;
import static com.theminespirit.guards.MechanicsModuleImports.violations;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UiModuleGuardTests {
    private static final Path MAIN = Path.of("ui/src/main/java");
    private static final Path TEST = Path.of("ui/src/test/java");
    private static final Path PACKAGE = Path.of("com/theminespirit");
    private static final List<String> OWN_PACKAGES = List.of("core.ui");
    private static final List<String> MODULE_PACKAGES =
            List.of("core.rest", "core.board", "core.building", "core.town", "core.mining");

    @Test
    void uiSources_LiveInUiModule_NotInRoot() {
        assertTrue(
                Files.isDirectory(MAIN.resolve(PACKAGE).resolve("core/ui")),
                "UI не собран отдельным модулем: ui/src/main/java/com/theminespirit/core/ui отсутствует. "
                        + "Как надо: исходники экранов физически в :ui, пакеты прежние.");
        List<String> misplaced = new ArrayList<>();
        for (String logical : List.of(
                "core/ui/BoardSnapshot.java",
                "core/ui/BoardFeed.java",
                "core/ui/TerrainRaster.java",
                "core/ui/TownMapView.java")) {
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
                    misplaced.add("вне :ui: " + logical);
                }
            }
        });
        assertEquals(
                List.of(),
                misplaced,
                "Классы экранов обязаны лежать в :ui (кроме контрактов :api): иначе правка модели экрана снова "
                        + "гоняет все тесты корня, а модуль не собирается сам. Нарушители:");
    }

    @Test
    void uiTests_LiveInUiModule_LocalTestsExist() {
        assertTrue(
                Files.isDirectory(TEST.resolve(PACKAGE).resolve("core/ui")),
                "У :ui нет своих тестов (ui/src/test/java): перенос тестов молча отключил проверки экранов.");
        assertTrue(
                Files.isRegularFile(TEST.resolve(PACKAGE).resolve("core/ui/BoardSnapshotTests.java")),
                "Тест BoardSnapshotTests обязан переехать в :ui: иначе ./gradlew :ui:test проверяет не экраны.");
        List<String> misplaced = new ArrayList<>();
        SourceFiles.allTests().forEach((file, text) -> {
            String logical = SourceFiles.logical(file);
            for (String own : OWN_PACKAGES) {
                if (logical.startsWith(own.replace('.', '/') + "/")
                        && !file.normalize().startsWith(TEST)
                        && !file.normalize().startsWith(SourceFiles.API_TEST)) {
                    misplaced.add("вне :ui: " + logical);
                }
            }
        });
        assertEquals(
                List.of(),
                misplaced,
                "Тесты экранов обязаны лежать в :ui: иначе :ui:test не проверяет модуль, а корень гоняет чужие "
                        + "тесты на каждую правку. Нарушители:");
    }

    @Test
    void uiModule_Imports_OnlyApiAndDeclaredModules() {
        Set<String> apiClasses = apiClasses(SourceFiles.javaUnder(SourceFiles.apiPackage()));
        List<String> visible = new ArrayList<>(OWN_PACKAGES);
        visible.addAll(MODULE_PACKAGES);
        Map<Path, String> sources =
                Files.isDirectory(MAIN.resolve(PACKAGE)) ? SourceFiles.javaUnder(MAIN.resolve(PACKAGE)) : Map.of();
        assertEquals(
                List.of(),
                violations(sources, apiClasses, visible),
                "UI зависит только от :api, :rest, :board и объявленных механик: импорт корня (neoforge/client) "
                        + "возвращает цикл и запрещает отдельную сборку :ui. Как надо: экран — в :ui, обработчик "
                        + "клиента — в корне. Нарушители:");
    }

    @Test
    void uiTests_Imports_OnlyApiAndDeclaredModules() {
        Set<String> apiClasses = apiClasses(SourceFiles.javaUnder(SourceFiles.apiPackage()));
        List<String> visible = new ArrayList<>(OWN_PACKAGES);
        visible.addAll(MODULE_PACKAGES);
        Map<Path, String> sources =
                Files.isDirectory(TEST.resolve(PACKAGE)) ? SourceFiles.javaUnder(TEST.resolve(PACKAGE)) : Map.of();
        assertEquals(
                List.of(),
                violations(sources, apiClasses, visible),
                "Тест UI компилируется только с :api, своими и объявленными модулями: импорт корня в тесте "
                        + "скрывает, что :ui без корня не собрать. Нарушители:");
    }
}
