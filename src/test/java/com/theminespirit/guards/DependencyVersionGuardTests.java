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
    private static final List<String> LIBRARY_CONFIGS =
            List.of("implementation", "additionalRuntimeClasspath", "jarJar");
    private static final Set<String> KNOWN_CONFIGS = Set.of(
            "implementation", "additionalRuntimeClasspath", "jarJar", "api", "compileOnly",
            "compileOnlyApi", "runtimeOnly", "localRuntime", "annotationProcessor",
            "testImplementation", "testRuntimeOnly");
    private static final Pattern DEPENDENCIES_BLOCK = Pattern.compile("\\bdependencies\\s*\\{");
    private static final Pattern DECLARATION = Pattern.compile(
            "(?m)^\\s*(\\w+)\\s*(?:\\(\\s*)?(?:platform\\s*\\(\\s*)?[\"']([^\"']+)[\"']");
    private static final Pattern PROPERTY_VERSION = Pattern.compile("^\\$\\{([A-Za-z0-9_.]+)}$");

    @Test
    void buildGradle_CurrentDependencies_KeepVersionsInPropertiesAndThreeConfigs() {
        String build = read(BUILD);
        String properties = read(PROPERTIES);

        assertEquals(List.of(), offendersOf(build, properties),
                "Версии зависимостей и объявления библиотек живут в одном месте: литерал версии в build.gradle "
                        + "повторяет число и расходится с gradle.properties; библиотека без "
                        + "additionalRuntimeClasspath не находится в dev-запуске, без jarJar — на сервере игрока. "
                        + "Версию держи в gradle.properties, библиотеку объявляй в implementation, "
                        + "additionalRuntimeClasspath и jarJar. Правило: docs/CONVENTIONS.md → «Сборка». Нарушители");
    }

    @Test
    void dependenciesParser_LiteralVersion_IsReported() {
        String build = "dependencies {\n"
                + "    implementation \"com.example:lib:1.2.3\"\n"
                + "    additionalRuntimeClasspath \"com.example:lib:1.2.3\"\n"
                + "    jarJar(\"com.example:lib:1.2.3\")\n"
                + "}\n";

        List<String> offenders = offendersOf(build, "");

        assertTrue(offenders.stream().anyMatch(line -> line.contains("литерал версии")),
                "Сторож ослеп: версия 1.2.3 в build.gradle должна попадать в нарушители: " + offenders);
    }

    @Test
    void dependenciesParser_LibraryWithoutRuntimeAndJarJar_IsReported() {
        String build = "dependencies {\n"
                + "    implementation \"com.example:lib:${lib_version}\"\n"
                + "}\n";
        String properties = "lib_version=1.2.3\n";

        List<String> offenders = offendersOf(build, properties);

        assertTrue(offenders.stream().anyMatch(line -> line.contains("additionalRuntimeClasspath")),
                "Сторож ослеп: библиотека без additionalRuntimeClasspath не попадёт в dev-запуск, "
                        + "но нарушение не найдено: " + offenders);
        assertTrue(offenders.stream().anyMatch(line -> line.contains("jarJar")),
                "Сторож ослеп: библиотека без jarJar не попадёт на сервер игрока, но нарушение не найдено: "
                        + offenders);
    }

    @Test
    void dependenciesParser_TransitiveCompanionInJarJar_IsAccepted() {
        String build = "dependencies {\n"
                + "    implementation \"com.fasterxml.jackson.core:jackson-databind:${jackson_version}\"\n"
                + "    additionalRuntimeClasspath \"com.fasterxml.jackson.core:jackson-databind:${jackson_version}\"\n"
                + "    jarJar(\"com.fasterxml.jackson.core:jackson-databind:${jackson_version}\")\n"
                + "    jarJar(\"com.fasterxml.jackson.core:jackson-core:${jackson_version}\")\n"
                + "}\n";
        String properties = "jackson_version=2.18.2\n";

        assertEquals(List.of(), offendersOf(build, properties),
                "Сторож ложно срабатывает: jackson-core приходит транзитивно и объявлен в jarJar рядом "
                        + "с прямой библиотекой той же группы — это законное объявление");
    }

    @Test
    void dependenciesParser_UnknownVersionProperty_IsReported() {
        String build = "dependencies {\n"
                + "    implementation \"com.example:lib:${missing_version}\"\n"
                + "    additionalRuntimeClasspath \"com.example:lib:${missing_version}\"\n"
                + "    jarJar(\"com.example:lib:${missing_version}\")\n"
                + "}\n";

        List<String> offenders = offendersOf(build, "");

        assertTrue(offenders.stream().anyMatch(line -> line.contains("missing_version")),
                "Сторож ослеп: версия из несуществующего свойства gradle.properties должна попадать "
                        + "в нарушители: " + offenders);
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

    private static void checkVersion(String coordinate, Map<String, String> properties, int line,
            List<String> offenders) {
        String[] parts = coordinate.split(":");
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

    private static void require(String coordinate, String from, String to, Set<String> present,
            List<String> offenders) {
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
        for (String line : properties.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int equals = trimmed.indexOf('=');
            if (equals > 0) {
                values.put(trimmed.substring(0, equals).trim(), trimmed.substring(equals + 1).trim());
            }
        }
        return values;
    }

    private static String groupArtifact(String coordinate) {
        String[] parts = coordinate.split(":");
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
            throw new UncheckedIOException("Сторож не смог прочитать " + path
                    + ": тесты запускаются из корня проекта", e);
        }
    }
}
