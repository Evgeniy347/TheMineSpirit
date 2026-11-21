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
    private static final Path TEST_SCRIPT = Path.of("scripts", "test.sh");
    private static final Path PRE_COMMIT = Path.of("scripts", "git-hooks", "pre-commit");
    private static final Path NIGHTLY = Path.of("scripts", "nightly-analyzers.sh");

    @Test
    void gradleProperties_WithCachingOn_KeepSharedBuildCache() throws IOException {
        String properties = read(PROPERTIES);
        assertTrue(
                properties.contains("org.gradle.caching=true"),
                "Без org.gradle.caching=true SpotBugs/PMD перестают брать результаты из общего кэша worktree "
                        + "и check снова тратит на них минуты (83% времени)"
                        + " Иначе тест gradleProperties_WithCachingOn_KeepSharedBuildCache перестанет ловить эту поломку. При смене gradleProperties_WithCachingOn_KeepSharedBuildCache проверь это утверждение.");
    }

    @Test
    void spotbugs_WithModVersion_ReleaseStaysStable() throws IOException {
        String conventions = read(CONVENTIONS);
        assertTrue(
                conventions.contains("release.set(versionOf('mod_version_minor'))"),
                "Метка release у SpotBugs должна быть стабильной (минор версии): project.version у :mod — 0.1.N, "
                        + "меняется каждым коммитом и сбрасывает кэш spotbugsMain/spotbugsTest"
                        + " Иначе тест spotbugs_WithModVersion_ReleaseStaysStable перестанет ловить эту поломку. При смене spotbugs_WithModVersion_ReleaseStaysStable проверь это утверждение.");
        assertFalse(
                conventions.contains("release.set(project.version")
                        || conventions.contains("release = project.version"),
                "release = project.version делает ключ кэша SpotBugs зависимым от номера коммита: неизменный код "
                        + "анализируется заново после каждого коммита"
                        + " Иначе тест spotbugs_WithModVersion_ReleaseStaysStable перестанет ловить эту поломку. При смене spotbugs_WithModVersion_ReleaseStaysStable проверь это утверждение.");
    }

    @Test
    void spotbugs_WithSourceDirs_SourcesStayOutOfCacheKey() throws IOException {
        String conventions = read(CONVENTIONS);
        assertTrue(
                conventions.contains("tasks.named('spotbugsMain')") && conventions.contains("sourceDirs.setFrom()"),
                "sourceDirs SpotBugs входят в ключ кэша содержимым файлов: правка комментария повторяла весь "
                        + "анализ. Исходники в отчёте SpotBugs берёт из debug-информации классов, а sourceDirs "
                        + "должны оставаться пустыми"
                        + " Иначе тест spotbugs_WithSourceDirs_SourcesStayOutOfCacheKey перестанет ловить эту поломку. При смене spotbugs_WithSourceDirs_SourcesStayOutOfCacheKey проверь это утверждение.");
        assertFalse(
                conventions.contains("sourceDirs.setFrom(sourceSets"),
                "sourceDirs снова привязаны к каталогам java: любая правка комментария сбрасывает кэш SpotBugs "
                        + "и гоняет анализ без изменений байткода"
                        + " Иначе тест spotbugs_WithSourceDirs_SourcesStayOutOfCacheKey перестанет ловить эту поломку. При смене spotbugs_WithSourceDirs_SourcesStayOutOfCacheKey проверь это утверждение.");
    }

    @Test
    void pmdTestFixtures_WithoutRuleSet_FailsCheck() throws IOException {
        String conventions = read(CONVENTIONS);
        assertTrue(
                conventions.contains("tasks.named('pmdTestFixtures', Pmd)")
                        && conventions.contains("ruleSetFiles = files(configAt('pmd/test.xml'))"),
                "java-test-fixtures создаёт pmdTestFixtures, а pmd { ruleSets = [] } оставляет её без набора правил: "
                        + "задача падает «No rulesets specified» и валит полный check"
                        + " Иначе тест pmdTestFixtures_WithoutRuleSet_FailsCheck перестанет ловить эту поломку. При смене pmdTestFixtures_WithoutRuleSet_FailsCheck проверь это утверждение.");
        assertTrue(
                conventions.contains("tasks.named('spotbugsTestFixtures')")
                        && conventions.contains("excludeFilter = configAt('spotbugs/exclude-test.xml')"),
                "spotbugsTestFixtures проверяет тестовые фикстуры: ему нужен тот же набор исключений, что и spotbugsTest "
                        + "(Random с зерном — воспроизводимый генератор сценария)"
                        + " При смене pmdTestFixtures_WithoutRuleSet_FailsCheck проверь это утверждение.");
    }

    @Test
    void gametestSpotbugs_WithModClasses_AnalyzesOnlyOwn() throws IOException {
        String gametest = read(GAMETEST);
        assertTrue(
                gametest.contains("classDirs.setFrom(sourceSets.main.java.destinationDirectory)"),
                "spotbugsMain :gametest должен анализировать только собственные классы :gametest: в его output "
                        + "склеены 1000+ классов :mod, и повторный анализ стоил ~90 с без новых находок"
                        + " Иначе тест gametestSpotbugs_WithModClasses_AnalyzesOnlyOwn перестанет ловить эту поломку. При смене gametestSpotbugs_WithModClasses_AnalyzesOnlyOwn проверь это утверждение.");
        assertTrue(
                gametest.contains("dependsOn tasks.named('compileJava')"),
                "У spotbugsMain :gametest должен быть явный dependsOn compileJava: classDirs из одного "
                        + "destinationDirectory больше не несут встроенной зависимости, и Gradle валит граф"
                        + " Иначе тест gametestSpotbugs_WithModClasses_AnalyzesOnlyOwn перестанет ловить эту поломку. При смене gametestSpotbugs_WithModClasses_AnalyzesOnlyOwn проверь это утверждение.");
    }

    @Test
    void normalCycle_HeavyAnalyzers_AreNotRequested() throws IOException {
        String properties = read(PROPERTIES);
        String conventions = read(CONVENTIONS);
        String test = read(TEST_SCRIPT);
        String hook = read(PRE_COMMIT);

        assertTrue(
                properties.contains("theminespirit.analyzers=off"),
                "Ночной прогон и приёмка включают SpotBugs/PMD/Checkstyle флагом -Ptheminespirit.analyzers=on, "
                        + "а обычная сборка обязана оставаться быстрой: в gradle.properties нужен "
                        + "theminespirit.analyzers=off (владелец 07.11, анализаторы ≤30% времени)"
                        + " Иначе тест normalCycle_HeavyAnalyzers_AreNotRequested перестанет ловить эту поломку. При смене normalCycle_HeavyAnalyzers_AreNotRequested проверь это утверждение.");
        assertTrue(
                conventions.contains("findProperty('theminespirit.analyzers') != 'on'"),
                "Без выключения по умолчанию SpotBugs/PMD/Checkstyle снова попадают в каждый check и съедают "
                        + "57–81% времени (docs/research/analyzers-cost.md)"
                        + " Иначе тест normalCycle_HeavyAnalyzers_AreNotRequested перестанет ловить эту поломку. При смене normalCycle_HeavyAnalyzers_AreNotRequested проверь это утверждение.");
        assertTrue(
                test.contains("formatCheck"),
                "scripts/test.sh обязан гонять в обычном цикле только формат Spotless (formatCheck): "
                        + "Error Prone живёт в компиляторе, тяжёлые анализаторы — ночью и на приёмке"
                        + " Иначе тест normalCycle_HeavyAnalyzers_AreNotRequested перестанет ловить эту поломку. При смене normalCycle_HeavyAnalyzers_AreNotRequested проверь это утверждение.");
        assertFalse(
                test.contains("staticAnalysis"),
                "scripts/test.sh снова зовёт staticAnalysis/staticAnalysisFast: это Checkstyle, PMD, SpotBugs "
                        + "в обычном цикле — красный по времени и против правила 30%"
                        + " Иначе тест normalCycle_HeavyAnalyzers_AreNotRequested перестанет ловить эту поломку. При смене normalCycle_HeavyAnalyzers_AreNotRequested проверь это утверждение.");
        assertFalse(
                hook.contains("analyzers=on")
                        || hook.contains("spotbugs")
                        || hook.contains("pmdMain")
                        || hook.contains("checkstyle"),
                "pre-commit не должен включать SpotBugs/PMD/Checkstyle: он зовёт scripts/test.sh, а тяжёлый "
                        + "анализ — дело ночного прогона"
                        + " Иначе тест normalCycle_HeavyAnalyzers_AreNotRequested перестанет ловить эту поломку. При смене normalCycle_HeavyAnalyzers_AreNotRequested проверь это утверждение.");
        assertTrue(
                Files.isRegularFile(NIGHTLY),
                "Нет scripts/nightly-analyzers.sh: полный набор анализаторов и тестов по main тогда негде "
                        + "запускать, и находки анализаторов останутся незамеченными"
                        + " Иначе тест normalCycle_HeavyAnalyzers_AreNotRequested перестанет ловить эту поломку. При смене normalCycle_HeavyAnalyzers_AreNotRequested проверь это утверждение.");
    }

    @Test
    void cacheScript_WithoutLiveCheck_CacheRegressionIsInvisible() {
        assertTrue(
                Files.isRegularFile(CACHE_SCRIPT),
                "Нет scripts/testing/check-static-analysis-cache.sh: нечем проверить, что spotbugsMain/pmdMain "
                        + "остаются UP-TO-DATE/FROM-CACHE и :gametest не анализирует классы :mod"
                        + " Иначе тест cacheScript_WithoutLiveCheck_CacheRegressionIsInvisible перестанет ловить эту поломку. При смене cacheScript_WithoutLiveCheck_CacheRegressionIsInvisible проверь это утверждение.");
    }

    private static String read(Path path) throws IOException {
        assertTrue(
                Files.isRegularFile(path),
                "Сторож не нашёл " + path + ": тесты запускаются из корня проекта"
                        + " Иначе тест cacheScript_WithoutLiveCheck_CacheRegressionIsInvisible перестанет ловить эту поломку. При смене cacheScript_WithoutLiveCheck_CacheRegressionIsInvisible проверь это утверждение.");
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
