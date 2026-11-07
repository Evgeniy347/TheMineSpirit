package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class BuildRepositoriesGuardTests {
    private static final Path ROOT = Path.of(".");
    private static final Path SETTINGS = Path.of("settings.gradle");
    private static final Path BUILD_LOGIC_SETTINGS = Path.of("build-logic", "settings.gradle");
    private static final Set<String> REPOSITORY_OWNERS = Set.of(normalize(SETTINGS), normalize(BUILD_LOGIC_SETTINGS));
    private static final Set<String> SKIPPED_DIRS =
            Set.of("build", ".gradle", ".git", ".idea", ".codegraph", "TestResults", "node_modules", "run", "logs");
    private static final String PUBLISHING = "publishing";

    @Test
    void buildScripts_DeclareDependencyRepositories_OnlyInSettings() {
        assertEquals(
                List.of(),
                offendersInTree(),
                "Репозитории зависимостей объявлены в build-файле: project-уровень repositories выключает "
                        + "dependencyResolutionManagement из settings (Gradle 9), и кэш, прогретый с одним набором "
                        + "репозиториев, перестаёт годиться для другого — offline-сборка сервера падает на "
                        + "«No cached version … available for offline mode». Объявляй репозитории только в "
                        + "settings.gradle и build-logic/settings.gradle. Нарушители");
    }

    @Test
    void settings_ManageRepositories_WithExplicitMode() {
        String root = read(SETTINGS);
        String buildLogic = read(BUILD_LOGIC_SETTINGS);
        for (String where : List.of("settings.gradle", "build-logic/settings.gradle")) {
            String text = where.startsWith("build-logic") ? buildLogic : root;
            assertTrue(
                    text.contains("dependencyResolutionManagement"),
                    where + " без dependencyResolutionManagement: репозитории снова расползутся по build-файлам, "
                            + "и чистая offline-сборка не найдёт зависимости");
            assertTrue(
                    text.contains("RepositoriesMode."),
                    where + " без явного repositoriesMode: default PREFER_PROJECT молча игнорирует репозитории "
                            + "из settings, как только project объявит свой — кэш и сборка расходятся");
            assertTrue(
                    text.contains("mavenCentral()"),
                    where + " → dependencyResolutionManagement без mavenCentral(): JUnit, Error Prone, PMD, SpotBugs "
                            + "не скачаются на чистом кэше");
        }
        assertTrue(
                root.contains("gradlePluginPortal()"),
                "settings.gradle → pluginManagement без gradlePluginPortal(): плагины сборки не разрешатся");
        assertTrue(
                root.contains("https://maven.neoforged.net/releases"),
                "settings.gradle без maven.neoforged.net: moddev отдаёт net.neoforged:neoforge только оттуда, "
                        + "и project-уровень moddev для этого обходится");
        assertTrue(
                root.contains("https://maven.blamejared.com"),
                "settings.gradle без maven.blamejared.com: compileOnly JEI (mezz.jei) не разрешится");
        int managed = root.indexOf("dependencyResolutionManagement");
        int mojangMeta = root.indexOf("'Mojang Meta'", managed);
        int neoforged = root.indexOf("'NeoForged Releases'", managed);
        assertTrue(
                mojangMeta > managed && mojangMeta < neoforged,
                "settings.gradle: репозиторий «Mojang Meta» должен идти раньше «NeoForged Releases» — "
                        + "net.neoforged:minecraft-dependencies лежит только в mojang-meta, и иначе Gradle ищет "
                        + "его на maven.neoforged.net/releases, а сбой сети отключает репозиторий на весь прогон");
        assertTrue(
                buildLogic.contains("https://maven.neoforged.net/releases"),
                "build-logic/settings.gradle без maven.neoforged.net: moddev-gradle не найдётся, если плагин-портал "
                        + "не отдал его (на сервере это и валило сборку)");
        assertTrue(
                buildLogic.contains("gradlePluginPortal()"),
                "build-logic/settings.gradle без gradlePluginPortal(): spotless/moddev/errorprone/spotbugs "
                        + "не разрешатся на чистом кэше");
    }

    @Test
    void repositoriesParser_ProjectRepositories_AreReported() {
        String build = """
                       plugins {
                           id 'java'
                       }

                       repositories {
                           mavenCentral()
                       }
                       """;

        List<String> offenders = projectRepositories(build);
        int expectedLine = (int) build.substring(0, build.indexOf("repositories {"))
                        .chars()
                        .filter(c -> c == '\n')
                        .count()
                + 1;

        assertEquals(1, offenders.size(), "Сторож ослеп: project-уровень repositories не найден: " + offenders);
        assertTrue(
                offenders.get(0).contains(":" + expectedLine + " "),
                "Сторож должен называть строку нарушения (" + expectedLine + "), получено: " + offenders);
    }

    @Test
    void repositoriesParser_PublishingRepositoriesAndComments_AreAllowed() {
        String build = """
                       // repositories { mavenCentral() }
                       /* repositories { mavenCentral() } */
                       plugins {
                           id 'maven-publish'
                       }

                       publishing {
                           publications {
                               register('mavenJava', MavenPublication) {
                                   from components.java
                               }
                           }
                           repositories {
                               maven {
                                   url "file://${project.projectDir}/repo"
                               }
                           }
                       }

                       tasks.register('show') {
                           def text = "repositories { mavenCentral() }"
                           doLast { println text }
                       }
                       """;

        assertEquals(
                List.of(),
                projectRepositories(build),
                "Сторож ложно срабатывает: repositories внутри publishing — цель публикации, а не источник "
                        + "зависимостей; шаблон в комментарии и строке — не объявление");
    }

    private static List<String> offendersInTree() {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(ROOT)) {
            files.filter(path -> Files.isRegularFile(path))
                    .filter(path -> path.getFileName().toString().endsWith(".gradle"))
                    .filter(path -> !skipped(path))
                    .filter(path -> !REPOSITORY_OWNERS.contains(normalize(path)))
                    .sorted()
                    .forEach(path -> projectRepositories(read(path))
                            .forEach(offender -> offenders.add(normalize(path) + offender)));
        } catch (IOException error) {
            throw new UncheckedIOException("Сторож не смог обойти build-файлы от корня проекта", error);
        }
        return offenders;
    }

    private static boolean skipped(Path path) {
        for (Path part : path) {
            if (SKIPPED_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(Path path) {
        String text = path.toString().replace('\\', '/');
        while (text.startsWith("./")) {
            text = text.substring(2);
        }
        return text;
    }

    private static List<String> projectRepositories(String text) {
        String code = withoutCommentsAndLiterals(text);
        List<String> offenders = new ArrayList<>();
        Deque<String> blocks = new ArrayDeque<>();
        StringBuilder word = new StringBuilder();
        int line = 1;
        for (int i = 0; i < code.length(); i++) {
            char current = code.charAt(i);
            if (current == '\n') {
                line++;
            }
            if (Character.isLetterOrDigit(current) || current == '_' || current == '.') {
                word.append(current);
                continue;
            }
            if (current == '{') {
                String block = word.toString();
                if ("repositories".equals(block) && !blocks.contains(PUBLISHING)) {
                    offenders.add(":" + line + " — repositories {}");
                }
                blocks.push(block);
                word.setLength(0);
                continue;
            }
            if (current == '}') {
                if (!blocks.isEmpty()) {
                    blocks.pop();
                }
                word.setLength(0);
                continue;
            }
            if (!Character.isWhitespace(current)) {
                word.setLength(0);
            }
        }
        return offenders;
    }

    private static String withoutCommentsAndLiterals(String text) {
        StringBuilder result = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            char current = text.charAt(i);
            if (current == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                while (i < text.length() && text.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (current == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < text.length() && !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) {
                    if (text.charAt(i) == '\n') {
                        result.append('\n');
                    }
                    i++;
                }
                i += 2;
                continue;
            }
            if (current == '\'' || current == '"') {
                char quote = current;
                i++;
                while (i < text.length() && text.charAt(i) != quote) {
                    if (text.charAt(i) == '\\') {
                        i++;
                    }
                    if (i < text.length() && text.charAt(i) == '\n') {
                        result.append('\n');
                    }
                    i++;
                }
                i++;
                result.append("''");
                continue;
            }
            result.append(current);
            i++;
        }
        return result.toString();
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException error) {
            throw new UncheckedIOException("Сторож не смог прочитать " + path + ": тесты запускаются из корня", error);
        }
    }
}
