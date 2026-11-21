package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ModVersionWiringGuardTests {
    private static final Path BUILD = Path.of("mod/build.gradle");
    private static final Path PROPERTIES = Path.of("gradle.properties");
    private static final Path TEMPLATE = SourceFiles.TEMPLATES.resolve("META-INF/neoforge.mods.toml");

    @Test
    void buildGradle_BuildNumber_ComesFromGitOrExplicitProperty() throws IOException {
        String build = read(BUILD);

        assertTrue(
                build.contains("git")
                        && build.contains("rev-list")
                        && build.contains("--count")
                        && build.contains("HEAD"),
                "Номер сборки обязан считаться из git rev-list --count HEAD: ручная правка версии расходится с историей"
                        + " Иначе тест buildGradle_BuildNumber_ComesFromGitOrExplicitProperty перестанет ловить эту поломку. При смене buildGradle_BuildNumber_ComesFromGitOrExplicitProperty проверь это утверждение.");
        assertTrue(
                build.contains("build_number"),
                "Без git должен быть явный номер -Pbuild_number=N, иначе сборка не знает, какую версию писать"
                        + " При смене buildGradle_BuildNumber_ComesFromGitOrExplicitProperty проверь это утверждение.");
        assertTrue(
                build.contains("mod_version_minor") && build.contains("modVersion"),
                "Минор берётся из gradle.properties, а итоговая версия 0.1.N — из минора и номера сборки"
                        + " Иначе тест buildGradle_BuildNumber_ComesFromGitOrExplicitProperty перестанет ловить эту поломку. При смене buildGradle_BuildNumber_ComesFromGitOrExplicitProperty проверь это утверждение.");
    }

    @Test
    void properties_WithMinorOnly_HaveNoFullVersion() throws IOException {
        String properties = read(PROPERTIES);

        assertTrue(
                properties.contains("mod_version_minor=0.1"),
                "В gradle.properties живёт только минор 0.1: он поднимается вручную"
                        + " Иначе тест properties_WithMinorOnly_HaveNoFullVersion перестанет ловить эту поломку. При смене properties_WithMinorOnly_HaveNoFullVersion проверь это утверждение.");
        assertFalse(
                properties.contains("\nmod_version="),
                "Полной версии 0.1.0 в gradle.properties быть не должно: номер сборки считает build.gradle"
                        + " Иначе тест properties_WithMinorOnly_HaveNoFullVersion перестанет ловить эту поломку. При смене properties_WithMinorOnly_HaveNoFullVersion проверь это утверждение.");
    }

    @Test
    void template_Version_ExpandedFromBuild() throws IOException {
        assertTrue(
                read(TEMPLATE).contains("version=\"${mod_version}\""),
                "neoforge.mods.toml обязан брать версию из подстановки сборки, а не хранить число сам"
                        + " Иначе тест template_Version_ExpandedFromBuild перестанет ловить эту поломку. При смене template_Version_ExpandedFromBuild проверь это утверждение.");
    }

    private static String read(Path path) throws IOException {
        assertTrue(
                Files.isRegularFile(path),
                "Сторож не нашёл " + path + ": тесты запускаются из корня проекта"
                        + " Иначе тест template_Version_ExpandedFromBuild перестанет ловить эту поломку. При смене template_Version_ExpandedFromBuild проверь это утверждение.");
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
