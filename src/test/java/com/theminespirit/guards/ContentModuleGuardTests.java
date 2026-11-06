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

class ContentModuleGuardTests {
    private static final Path MAIN = Path.of("content/src/main/java");
    private static final Path TEST = Path.of("content/src/test/java");
    private static final Path PACKAGE = Path.of("com/theminespirit");
    private static final List<String> OWN_PACKAGES = List.of("core.reload", "core.script");
    private static final List<String> MODULE_PACKAGES = List.of(
            "core.board",
            "core.tactics",
            "core.intellect",
            "core.processing",
            "core.policy",
            "core.building",
            "core.town",
            "core.movement",
            "core.route",
            "core.inventory",
            "core.mining",
            "core.farming",
            "core.forestry",
            "core.husbandry",
            "core.armor",
            "core.doors",
            "core.hunger",
            "core.repair");

    @Test
    void contentSources_LiveInContentModule_NotInRoot() {
        assertTrue(
                Files.isDirectory(MAIN.resolve(PACKAGE).resolve("core/reload")),
                "Данные и каталог не собраны модулем :content: content/src/main/java/com/theminespirit/core/reload "
                        + "отсутствует. Как надо: core.reload и core.script физически в :content, пакеты прежние.");
        List<String> misplaced = new ArrayList<>();
        for (String logical : List.of(
                "core/reload/DataTables.java",
                "core/reload/TableRegistry.java",
                "core/script/CoreScripts.java",
                "core/script/ParamDefaults.java")) {
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
                    misplaced.add("вне :content: " + logical);
                }
            }
        });
        assertEquals(
                List.of(),
                misplaced,
                "Таблицы и каталог скриптов обязаны лежать в :content (кроме контрактов :api): иначе правка данных "
                        + "снова гоняет все тесты корня, а модуль не собирается сам. Нарушители:");
    }

    @Test
    void contentTests_LiveInContentModule_LocalTestsExist() {
        assertTrue(
                Files.isDirectory(TEST.resolve(PACKAGE).resolve("core/script")),
                "У :content нет своих тестов (content/src/test/java/com/theminespirit/core/script): перенос тестов "
                        + "молча отключил проверки каталога.");
        assertTrue(
                Files.isRegularFile(TEST.resolve(PACKAGE).resolve("core/reload/TableRegistryTests.java")),
                "Тест TableRegistryTests обязан переехать в :content: иначе ./gradlew :content:test проверяет не "
                        + "таблицы.");
        List<String> misplaced = new ArrayList<>();
        SourceFiles.allTests().forEach((file, text) -> {
            String logical = SourceFiles.logical(file);
            for (String own : OWN_PACKAGES) {
                if (logical.startsWith(own.replace('.', '/') + "/")
                        && !file.normalize().startsWith(TEST)
                        && !file.normalize().startsWith(SourceFiles.API_TEST)) {
                    misplaced.add("вне :content: " + logical);
                }
            }
        });
        assertEquals(
                List.of(),
                misplaced,
                "Тесты таблиц и каталога обязаны лежать в :content: иначе :content:test не проверяет модуль, а "
                        + "корень гоняет чужие тесты на каждую правку. Нарушители:");
    }

    @Test
    void contentModule_Imports_OnlyApiAndDeclaredModules() {
        Set<String> apiClasses = apiClasses(SourceFiles.javaUnder(SourceFiles.apiPackage()));
        List<String> visible = new ArrayList<>(OWN_PACKAGES);
        visible.addAll(MODULE_PACKAGES);
        assertEquals(
                List.of(),
                violations(SourceFiles.javaUnder(MAIN.resolve(PACKAGE)), apiClasses, visible),
                "Контент зависит только от :api, :board, :tactics, :policy и объявленных механик: импорт корня "
                        + "(neoforge, gametest, rest) возвращает цикл и запрещает отдельную сборку :content. Как надо: "
                        + "нужный контракт — в :api, данные — у владельца. Нарушители:");
    }

    @Test
    void contentTests_Imports_OnlyApiAndDeclaredModules() {
        Set<String> apiClasses = apiClasses(SourceFiles.javaUnder(SourceFiles.apiPackage()));
        List<String> visible = new ArrayList<>(OWN_PACKAGES);
        visible.addAll(MODULE_PACKAGES);
        assertEquals(
                List.of(),
                violations(SourceFiles.javaUnder(TEST.resolve(PACKAGE)), apiClasses, visible),
                "Тест контента компилируется только с :api, своим и объявленными модулями: импорт корня в тесте "
                        + "скрывает, что :content без корня не собрать. Нарушители:");
    }
}
