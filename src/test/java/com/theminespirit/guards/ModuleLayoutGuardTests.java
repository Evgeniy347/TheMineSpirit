package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ModuleLayoutGuardTests {
    private static final Pattern INCLUDE = Pattern.compile("^\\s*include\\s+'(:[^']+)'", Pattern.MULTILINE);

    @Test
    void moduleRoots_HaveOneSource_SettingsGradle() throws IOException {
        String layout = Files.readString(Path.of("config/layout.properties"));
        assertFalse(
                layout.contains("module_roots=") || layout.contains("module_test_roots="),
                "Список корней модулей обязан быть в одном месте — settings.gradle: config/layout.properties "
                        + "снова завёл module_roots/module_test_roots. Как надо: новый модуль добавляется только "
                        + "через include в settings.gradle, остальные инструменты читают его список оттуда.");
        String paths = Files.readString(Path.of("scripts/module-paths.sh"));
        assertFalse(
                paths.contains("module_layout_value module_roots"),
                "scripts/module-paths.sh снова читает корни модулей из layout.properties: список разъедется с "
                        + "settings.gradle. Как надо: разбирай include-строки settings.gradle.");
        String logical = Files.readString(Path.of("tools/modules/logical_paths.py"));
        assertFalse(
                logical.contains("\"module_roots\""),
                "tools/modules/logical_paths.py снова читает корни модулей из layout.properties: список "
                        + "разъедется с settings.gradle. Как надо: разбирай include-строки settings.gradle.");
        String graph = Files.readString(Path.of("tools/modules/package_graph.py"));
        assertFalse(
                graph.contains("_layout_extra(\"module_roots\")"),
                "tools/modules/package_graph.py снова читает корни модулей из layout.properties: список "
                        + "разъедется с settings.gradle. Как надо: разбирай include-строки settings.gradle.");

        for (String id : modules()) {
            String directory = id.substring(1).replace(':', '/');
            assertTrue(
                    SourceFiles.moduleMainRoots().contains(Path.of(directory, "src/main/java")),
                    "Модуль " + id + " есть в settings.gradle, но SourceFiles не знает его корень исходников: "
                            + "сторожа и baseline снова начнут искать файлы не там.");
            assertTrue(
                    SourceFiles.moduleTestRoots().contains(Path.of(directory, "src/test/java")),
                    "Модуль " + id + " есть в settings.gradle, но SourceFiles не знает его корень тестов: "
                            + "тесты модуля выпадут из отчётов и baseline.");
        }
    }

    @Test
    void moduleBuildScripts_HardcodedReportsAndRootTests_AreAbsent() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String id : modules()) {
            Path build = Path.of(id.substring(1).replace(':', '/'), "build.gradle");
            if (!Files.isRegularFile(build)) {
                continue;
            }
            String text = Files.readString(build);
            if (text.contains("TestResults/")) {
                violations.add(build + ": путь отчёта тестов");
            }
            if (text.contains("sourceSets.test.output.classesDirs")
                    || text.contains("sourceSets.test.runtimeClasspath")) {
                violations.add(build + ": тестовые классы корня");
            }
        }
        assertTrue(
                violations.isEmpty(),
                "Модуль не повторяет пути отчётов и не тянет тестовые классы корня: иначе отчёт теста уходит в "
                        + "literal TestResults/${project.name}, а тест модуля без корня не собрать. Как надо: отчёт "
                        + "и фикстуры настраивает build-logic (:test-fixtures). Нарушители: " + violations);
    }

    private static List<String> modules() throws IOException {
        String settings = Files.readString(Path.of("settings.gradle"));
        Matcher matcher = INCLUDE.matcher(settings);
        List<String> modules = new ArrayList<>();
        while (matcher.find()) {
            String id = matcher.group(1);
            if (!":mod".equals(id) && !":api".equals(id)) {
                modules.add(id);
            }
        }
        return modules;
    }
}
