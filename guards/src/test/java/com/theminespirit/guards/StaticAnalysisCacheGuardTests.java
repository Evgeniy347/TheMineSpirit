package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StaticAnalysisCacheGuardTests {
    private static final Path PROPERTIES = Path.of("gradle.properties");
    private static final Path CONVENTIONS =
            Path.of("build-logic", "src", "main", "groovy", "theminespirit.java-conventions.gradle");
    private static final Path GAMETEST = Path.of("gametest", "build.gradle");
    private static final Path CACHE_SCRIPT = Path.of("scripts", "testing", "check-static-analysis-cache.sh");

    @Test
    void gradleProperties_WithCachingOn_KeepSharedBuildCache() throws IOException {
        String properties = read(PROPERTIES);
        assertTrue(
                properties.contains("org.gradle.caching=true"),
                "Без org.gradle.caching=true SpotBugs/PMD перестают брать результаты из общего кэша worktree "
                        + "и check снова тратит на них минуты (83% времени)");
    }

    @Test
    void spotbugs_WithModVersion_ReleaseStaysStable() throws IOException {
        String conventions = read(CONVENTIONS);
        assertTrue(
                conventions.contains("release.set(versionOf('mod_version_minor'))"),
                "Метка release у SpotBugs должна быть стабильной (минор версии): project.version у :mod — 0.1.N, "
                        + "меняется каждым коммитом и сбрасывает кэш spotbugsMain/spotbugsTest");
        assertFalse(
                conventions.contains("release.set(project.version")
                        || conventions.contains("release = project.version"),
                "release = project.version делает ключ кэша SpotBugs зависимым от номера коммита: неизменный код "
                        + "анализируется заново после каждого коммита");
    }

    @Test
    void spotbugs_WithResourceDirs_SourcePathOnlyJavaSources() throws IOException {
        String conventions = read(CONVENTIONS);
        assertTrue(
                conventions.contains("tasks.named('spotbugsMain')")
                        && conventions.contains("sourceDirs.setFrom(sourceSets.main.allJava.srcDirs)")
                        && conventions.contains("sourceDirs.setFrom(sourceSets.test.allJava.srcDirs)"),
                "sourceDirs SpotBugs — это только java-исходники своей области: каталоги ресурсов в sourcepath "
                        + "заставляли повторять анализ после правки любого json");
    }

    @Test
    void pmdTestFixtures_WithoutRuleSet_FailsCheck() throws IOException {
        String conventions = read(CONVENTIONS);
        assertTrue(
                conventions.contains("tasks.named('pmdTestFixtures', Pmd)")
                        && conventions.contains("ruleSetFiles = files(configAt('pmd/test.xml'))"),
                "java-test-fixtures создаёт pmdTestFixtures, а pmd { ruleSets = [] } оставляет её без набора правил: "
                        + "задача падает «No rulesets specified» и валит полный check");
        assertTrue(
                conventions.contains("tasks.named('spotbugsTestFixtures')")
                        && conventions.contains("excludeFilter = configAt('spotbugs/exclude-test.xml')"),
                "spotbugsTestFixtures проверяет тестовые фикстуры: ему нужен тот же набор исключений, что и spotbugsTest "
                        + "(Random с зерном — воспроизводимый генератор сценария)");
    }

    @Test
    void gametestSpotbugs_WithModClasses_AnalyzesOnlyOwn() throws IOException {
        String gametest = read(GAMETEST);
        assertTrue(
                gametest.contains("classDirs.setFrom(sourceSets.main.java.destinationDirectory)"),
                "spotbugsMain :gametest должен анализировать только собственные классы :gametest: в его output "
                        + "склеены 1000+ классов :mod, и повторный анализ стоил ~90 с без новых находок");
        assertTrue(
                gametest.contains("dependsOn tasks.named('compileJava')"),
                "У spotbugsMain :gametest должен быть явный dependsOn compileJava: classDirs из одного "
                        + "destinationDirectory больше не несут встроенной зависимости, и Gradle валит граф");
    }

    @Test
    void cacheScript_WithoutLiveCheck_CacheRegressionIsInvisible() {
        assertTrue(
                Files.isRegularFile(CACHE_SCRIPT),
                "Нет scripts/testing/check-static-analysis-cache.sh: нечем проверить, что spotbugsMain/pmdMain "
                        + "остаются UP-TO-DATE/FROM-CACHE и :gametest не анализирует классы :mod");
    }

    private static String read(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), "Сторож не нашёл " + path + ": тесты запускаются из корня проекта");
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
