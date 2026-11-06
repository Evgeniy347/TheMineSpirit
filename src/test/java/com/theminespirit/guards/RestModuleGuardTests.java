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

class RestModuleGuardTests {
    private static final Path MAIN = Path.of("rest/src/main/java");
    private static final Path TEST = Path.of("rest/src/test/java");
    private static final Path PACKAGE = Path.of("com/theminespirit");
    private static final List<String> OWN_PACKAGES = List.of("core.rest");
    private static final List<String> MODULE_PACKAGES = List.of(
            "core.board",
            "core.tactics",
            "core.intellect",
            "core.coordinator",
            "core.planner",
            "core.processing",
            "core.reload",
            "core.script",
            "core.policy");

    @Test
    void restSources_LiveInRestModule_NotInRoot() {
        assertTrue(
                Files.isDirectory(MAIN.resolve(PACKAGE).resolve("core/rest")),
                "REST не собран отдельным модулем: rest/src/main/java/com/theminespirit/core/rest отсутствует. "
                        + "Как надо: исходники rest физически в :rest, пакеты прежние.");
        List<String> misplaced = new ArrayList<>();
        for (String logical : List.of(
                "core/rest/Router.java",
                "core/rest/BoardRoutes.java",
                "core/rest/SseStream.java",
                "core/rest/TaskGateway.java")) {
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
                    misplaced.add("вне :rest: " + logical);
                }
            }
        });
        assertEquals(
                List.of(),
                misplaced,
                "Классы REST обязаны лежать в :rest (кроме контрактов :api): иначе правка транспорта снова гоняет "
                        + "все тесты корня, а модуль не собирается сам. Нарушители:");
    }

    @Test
    void restTests_LiveInRestModule_LocalTestsExist() {
        assertTrue(
                Files.isDirectory(TEST.resolve(PACKAGE).resolve("core/rest")),
                "У :rest нет своих тестов (rest/src/test/java): перенос тестов молча отключил проверки транспорта.");
        assertTrue(
                Files.isRegularFile(TEST.resolve(PACKAGE).resolve("core/rest/RouterTests.java")),
                "Тест RouterTests обязан переехать в :rest: иначе ./gradlew :rest:test проверяет не транспорт.");
        List<String> misplaced = new ArrayList<>();
        SourceFiles.allTests().forEach((file, text) -> {
            String logical = SourceFiles.logical(file);
            for (String own : OWN_PACKAGES) {
                if (logical.startsWith(own.replace('.', '/') + "/")
                        && !file.normalize().startsWith(TEST)
                        && !file.normalize().startsWith(SourceFiles.API_TEST)) {
                    misplaced.add("вне :rest: " + logical);
                }
            }
        });
        assertEquals(
                List.of(),
                misplaced,
                "Тесты REST обязаны лежать в :rest: иначе :rest:test не проверяет модуль, а корень гоняет чужие "
                        + "тесты на каждую правку. Нарушители:");
    }

    @Test
    void restModule_Imports_OnlyApiAndDeclaredModules() {
        Set<String> apiClasses = apiClasses(SourceFiles.javaUnder(SourceFiles.apiPackage()));
        List<String> visible = new ArrayList<>(OWN_PACKAGES);
        visible.addAll(MODULE_PACKAGES);
        Map<Path, String> sources =
                Files.isDirectory(MAIN.resolve(PACKAGE)) ? SourceFiles.javaUnder(MAIN.resolve(PACKAGE)) : Map.of();
        assertEquals(
                List.of(),
                violations(sources, apiClasses, visible),
                "REST зависит только от :api, :board, :tactics, :content и :policy, объявленных в build.gradle: "
                        + "импорт :ui возвращает цикл (ui → rest) и запрещает отдельную сборку :rest. Как надо: "
                        + "общий тип — контракт в :api, экран — в :ui. Нарушители:");
    }

    @Test
    void restTests_Imports_OnlyApiAndDeclaredModules() {
        Set<String> apiClasses = apiClasses(SourceFiles.javaUnder(SourceFiles.apiPackage()));
        List<String> visible = new ArrayList<>(OWN_PACKAGES);
        visible.addAll(MODULE_PACKAGES);
        Map<Path, String> sources =
                Files.isDirectory(TEST.resolve(PACKAGE)) ? SourceFiles.javaUnder(TEST.resolve(PACKAGE)) : Map.of();
        assertEquals(
                List.of(),
                violations(sources, apiClasses, visible),
                "Тест REST компилируется только с :api, своими и объявленными модулями: импорт :ui в тесте "
                        + "скрывает, что :rest без :ui не собрать, и возвращает цикл. Нарушители:");
    }
}
