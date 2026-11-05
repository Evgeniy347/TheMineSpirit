package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ModVersionWiringGuardTests {
    private static final Path BUILD = Path.of("build.gradle");
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
                "Номер сборки обязан считаться из git rev-list --count HEAD: ручная правка версии расходится с историей");
        assertTrue(
                build.contains("build_number"),
                "Без git должен быть явный номер -Pbuild_number=N, иначе сборка не знает, какую версию писать");
        assertTrue(
                build.contains("mod_version_minor") && build.contains("modVersion"),
                "Минор берётся из gradle.properties, а итоговая версия 0.1.N — из минора и номера сборки");
    }

    @Test
    void properties_WithMinorOnly_HaveNoFullVersion() throws IOException {
        String properties = read(PROPERTIES);

        assertTrue(
                properties.contains("mod_version_minor=0.1"),
                "В gradle.properties живёт только минор 0.1: он поднимается вручную");
        assertFalse(
                properties.contains("\nmod_version="),
                "Полной версии 0.1.0 в gradle.properties быть не должно: номер сборки считает build.gradle");
    }

    @Test
    void template_Version_ExpandedFromBuild() throws IOException {
        assertTrue(
                read(TEMPLATE).contains("version=\"${mod_version}\""),
                "neoforge.mods.toml обязан брать версию из подстановки сборки, а не хранить число сам");
    }

    private static String read(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), "Сторож не нашёл " + path + ": тесты запускаются из корня проекта");
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
