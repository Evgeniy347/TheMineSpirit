package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DependencyVersionGuardTests {
    private static final Path BUILD = Path.of("build.gradle");
    private static final Path PROPERTIES = Path.of("gradle.properties");
    private static final Path SETTINGS = Path.of("settings.gradle");

    private static final Set<String> KNOWN_CONFIGS = Set.of(
            "implementation",
            "additionalRuntimeClasspath",
            "jarJar",
            "api",
            "compileOnly",
            "compileOnlyApi",
            "runtimeOnly",
            "localRuntime",
            "annotationProcessor",
            "testImplementation",
            "testRuntimeOnly",
            "errorprone");
    private static final Pattern DEPENDENCIES_BLOCK = Pattern.compile("\\bdependencies\\s*\\{");
    private static final Pattern DECLARATION =
            Pattern.compile("(?m)^\\s*(\\w+)\\s*(?:\\(\\s*)?(?:platform\\s*\\(\\s*)?[\"']([^\"']+)[\"']");
    private static final Pattern PROPERTY_VERSION = Pattern.compile("^\\$\\{([A-Za-z0-9_.]+)}$");
    private static final Pattern PLUGINS_BLOCK = Pattern.compile("(?m)^\\s*plugins\\s*\\{");
    private static final Pattern PLUGIN_VERSION =
            Pattern.compile("(?m)^\\s*id\\s+['\"]([^'\"]+)['\"]\\s+version\\s+['\"]([^'\"]+)['\"]");
    private static final Pattern TOOL_VERSION_LITERAL =
            Pattern.compile("(?m)^\\s*(toolVersion\\s*=|palantirJavaFormat\\s*\\()\\s*['\"]([^'\"]*)['\"]");

    @Test
    void buildGradle_CurrentDependencies_KeepVersionsInPropertiesAndThreeConfigs() {
        String build = read(BUILD);
        String properties = read(PROPERTIES);

        assertEquals(
                List.of(),
                offendersOf(build, properties),
                "Версии зависимостей и объявления библиотек живут в одном месте: литерал версии в build.gradle "
                        + "повторяет число и расходится с gradle.properties; библиотека без "
                        + "additionalRuntimeClasspath не находится в dev-запуске, без jarJar — на сервере игрока. "
                        + "Версию держи в gradle.properties, библиотеку объявляй в implementation, "
                        + "additionalRuntimeClasspath и jarJar. Правило: docs/CONVENTIONS.md → «Сборка». Нарушители");
    }

    @Test
    void buildScripts_PluginAndToolVersions_LiveInProperties() {
        assertEquals(
                List.of(),
                pluginOffendersOf(read(BUILD), read(SETTINGS), read(PROPERTIES)),
                "Версии плагинов и инструментов сборки живут в gradle.properties: build.gradle объявляет плагин "
                        + "без версии, settings.gradle → pluginManagement подставляет ${…_version}, toolVersion "
                        + "и форматтер берут свойство. Литерал расходится с gradle.properties и обходит выбор "
                        + "версий пользователем. Правило: docs/CONVENTIONS.md → «Сборка». Нарушители");
    }

    @Test
    void pluginParser_LiteralVersions_AreReported() {
        String build = """
                       plugins {
                           id 'com.example.plugin' version '1.0'
                       }
                       checkstyle {
                           toolVersion = '10.0'
                       }
                       """;
        String settings = """
                          pluginManagement {
                              plugins {
                                  id 'com.example.other' version '2.0'
                                  id 'com.example.third' version "${missing_version}"
                              }
                          }
                          """;

        List<String> offenders = pluginOffendersOf(build, settings, "");

        assertEquals(
                4,
                offenders.size(),
                "Сторож ослеп: версия плагина в build.gradle, литерал в pluginManagement, несуществующее свойство "
                        + "и литерал toolVersion — четыре нарушения: " + offenders);
    }

    @Test
    void dependenciesParser_LiteralVersion_IsReported() {
        String build = """
                       dependencies {
                           implementation "com.example:lib:1.2.3"
                           additionalRuntimeClasspath "com.example:lib:1.2.3"
                           jarJar("com.example:lib:1.2.3")
                       }
                       """;

        List<String> offenders = offendersOf(build, "");

        assertTrue(
                offenders.stream().anyMatch(line -> line.contains("литерал версии")),
                "Сторож ослеп: версия 1.2.3 в build.gradle должна попадать в нарушители: " + offenders);
    }

    @Test
    void dependenciesParser_LibraryWithoutRuntimeAndJarJar_IsReported() {
        String build = """
                       dependencies {
                           implementation "com.example:lib:${lib_version}"
                       }
                       """;
        String properties = "lib_version=1.2.3\n";

        List<String> offenders = offendersOf(build, properties);

        assertTrue(
                offenders.stream().anyMatch(line -> line.contains("additionalRuntimeClasspath")),
                "Сторож ослеп: библиотека без additionalRuntimeClasspath не попадёт в dev-запуск, "
                        + "но нарушение не найдено: " + offenders);
        assertTrue(
                offenders.stream().anyMatch(line -> line.contains("jarJar")),
                "Сторож ослеп: библиотека без jarJar не попадёт на сервер игрока, но нарушение не найдено: "
                        + offenders);
    }

    @Test
    void dependenciesParser_TransitiveCompanionInJarJar_IsAccepted() {
        String build = """
                       dependencies {
                           implementation "com.fasterxml.jackson.core:jackson-databind:${jackson_version}"
                           additionalRuntimeClasspath "com.fasterxml.jackson.core:jackson-databind:${jackson_version}"
                           jarJar("com.fasterxml.jackson.core:jackson-databind:${jackson_version}")
                           jarJar("com.fasterxml.jackson.core:jackson-core:${jackson_version}")
                       }
                       """;
        String properties = "jackson_version=2.18.2\n";

        assertEquals(
                List.of(),
                offendersOf(build, properties),
                "Сторож ложно срабатывает: jackson-core приходит транзитивно и объявлен в jarJar рядом "
                        + "с прямой библиотекой той же группы — это законное объявление");
    }

    @Test
    void dependenciesParser_UnknownVersionProperty_IsReported() {
        String build = """
                       dependencies {
                           implementation "com.example:lib:${missing_version}"
                           additionalRuntimeClasspath "com.example:lib:${missing_version}"
                           jarJar("com.example:lib:${missing_version}")
                       }
                       """;

        List<String> offenders = offendersOf(build, "");

        assertTrue(
                offenders.stream().anyMatch(line -> line.contains("missing_version")),
                "Сторож ослеп: версия из несуществующего свойства gradle.properties должна попадать " + "в нарушители: "
                        + offenders);
    }

    private static List<String> offendersOf(String build, String properties) {
        Map<String, String> values = parseProperties(properties);
        List<String> offenders = new ArrayList<>();
        int blockOpen = dependenciesBlockOpen(build);
        if (blockOpen < 0) {
            return List.of("build.gradle — нет блока dependencies: без него нельзя проверить версии и конфигурации");
        }
        int blockEnd = matchingBrace(build, blockOpen);
        if (blockEnd < 0) {
            return List.of("build.gradle — блок dependencies не закрыт: сборка не разберётся");
        }
        String body = build.substring(blockOpen + 1, blockEnd);
        int baseLine = SourceFiles.lineOf(build, blockOpen);

        Map<String, Set<String>> byConfig = new LinkedHashMap<>();
        Matcher matcher = DECLARATION.matcher(body);
        while (matcher.find()) {
            String config = matcher.group(1);
            if (!KNOWN_CONFIGS.contains(config)) {
                continue;
            }
            String coordinate = matcher.group(2);
            String key = groupArtifact(coordinate);
            byConfig.computeIfAbsent(config, ignored -> new LinkedHashSet<>()).add(key);
            int line = baseLine + countNewlines(body, matcher.start());
            checkVersion(coordinate, values, line, offenders);
        }
        checkConfigurations(byConfig, offenders);
        return offenders;
    }

    private static List<String> pluginOffendersOf(String build, String settings, String properties) {
        Map<String, String> values = parseProperties(properties);
        List<String> offenders = new ArrayList<>();
        Matcher buildPlugin = PLUGIN_VERSION.matcher(blockBody(build, PLUGINS_BLOCK));
        while (buildPlugin.find()) {
            offenders.add("build.gradle — плагин " + buildPlugin.group(1) + " с версией «" + buildPlugin.group(2)
                    + "»: версия плагина — в settings.gradle → pluginManagement через свойство");
        }
        int management = settings.indexOf("pluginManagement");
        String managed = management < 0 ? "" : blockBody(settings.substring(management), PLUGINS_BLOCK);
        Matcher settingsPlugin = PLUGIN_VERSION.matcher(managed);
        while (settingsPlugin.find()) {
            String version = settingsPlugin.group(2);
            Matcher property = PROPERTY_VERSION.matcher(version);
            if (!property.matches()) {
                offenders.add("settings.gradle — плагин " + settingsPlugin.group(1) + ": литерал версии «" + version
                        + "», нужна ${…_version} из gradle.properties");
            } else if (!values.containsKey(property.group(1))) {
                offenders.add("settings.gradle — свойство " + property.group(1)
                        + " не найдено в gradle.properties: сборка упадёт на подстановке");
            }
        }
        Matcher tool = TOOL_VERSION_LITERAL.matcher(build);
        while (tool.find()) {
            offenders.add(
                    "build.gradle:" + SourceFiles.lineOf(build, tool.start()) + " — литерал версии «" + tool.group(2)
                            + "» в " + tool.group(1).trim() + ": версия инструмента — свойство gradle.properties");
        }
        return offenders;
    }

    private static String blockBody(String text, Pattern opening) {
        Matcher matcher = opening.matcher(text);
        if (!matcher.find()) {
            return "";
        }
        int open = text.indexOf('{', matcher.start());
        int end = matchingBrace(text, open);
        return end < 0 ? text.substring(open + 1) : text.substring(open + 1, end);
    }

    private static void checkVersion(
            String coordinate, Map<String, String> properties, int line, List<String> offenders) {
        String[] parts = coordinate.split(":", -1);
        if (parts.length < 3 || parts[2].isBlank()) {
            return;
        }
        Matcher property = PROPERTY_VERSION.matcher(parts[2]);
        if (!property.matches()) {
            offenders.add("build.gradle:" + line + " — литерал версии «" + parts[2] + "» у " + coordinate
                    + ": версия обязана быть ${…_version} из gradle.properties");
        } else if (!properties.containsKey(property.group(1))) {
            offenders.add("build.gradle:" + line + " — свойство " + property.group(1)
                    + " не найдено в gradle.properties: сборка упадёт на подстановке");
        }
    }

    private static void checkConfigurations(Map<String, Set<String>> byConfig, List<String> offenders) {
        Set<String> implementation = byConfig.getOrDefault("implementation", Set.of());
        Set<String> runtime = byConfig.getOrDefault("additionalRuntimeClasspath", Set.of());
        Set<String> jarJar = byConfig.getOrDefault("jarJar", Set.of());
        Set<String> implementationGroups = new LinkedHashSet<>();
        for (String coordinate : implementation) {
            implementationGroups.add(groupOf(coordinate));
        }
        for (String coordinate : implementation) {
            require(coordinate, "implementation", "additionalRuntimeClasspath", runtime, offenders);
            require(coordinate, "implementation", "jarJar", jarJar, offenders);
        }
        for (String coordinate : runtime) {
            require(coordinate, "additionalRuntimeClasspath", "implementation", implementation, offenders);
            require(coordinate, "additionalRuntimeClasspath", "jarJar", jarJar, offenders);
        }
        for (String coordinate : jarJar) {
            if (!implementation.contains(coordinate) && !implementationGroups.contains(groupOf(coordinate))) {
                offenders.add("build.gradle — " + coordinate + " объявлена в jarJar, но её группа не объявлена "
                        + "в implementation: сервер получит библиотеку, которой нет на compile classpath");
            }
        }
    }

    private static void require(
            String coordinate, String from, String to, Set<String> present, List<String> offenders) {
        if (!present.contains(coordinate)) {
            offenders.add("build.gradle — " + coordinate + " объявлена в " + from + ", но не в " + to
                    + ": без additionalRuntimeClasspath падает dev-запуск, без jarJar — сервер игрока");
        }
    }

    private static int dependenciesBlockOpen(String build) {
        Matcher matcher = DEPENDENCIES_BLOCK.matcher(build);
        return matcher.find() ? build.indexOf('{', matcher.start()) : -1;
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

    private static Map<String, String> parseProperties(String properties) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : properties.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int equals = trimmed.indexOf('=');
            if (equals > 0) {
                values.put(
                        trimmed.substring(0, equals).trim(),
                        trimmed.substring(equals + 1).trim());
            }
        }
        return values;
    }

    private static String groupArtifact(String coordinate) {
        String[] parts = coordinate.split(":", -1);
        return parts.length < 2 ? coordinate : parts[0] + ":" + parts[1];
    }

    private static String groupOf(String coordinate) {
        int colon = coordinate.indexOf(':');
        return colon < 0 ? coordinate : coordinate.substring(0, colon);
    }

    private static int countNewlines(String text, int end) {
        int count = 0;
        for (int i = 0; i < end; i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Сторож не смог прочитать " + path + ": тесты запускаются из корня проекта", e);
        }
    }
}
