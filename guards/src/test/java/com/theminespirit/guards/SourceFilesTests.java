package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SourceFilesTests {
    @Test
    void path_MainSource_ResolvesUnderMain() {
        Path file = SourceFiles.path("neoforge/board/GraphSource.java");

        assertEquals(
                SourceFiles.MAIN.resolve("com/theminespirit/neoforge/board/GraphSource.java"),
                file,
                "логический путь main-исходника обязан разворачиваться в MAIN, а не в TEST"
                        + " Иначе тест path_MainSource_ResolvesUnderMain перестанет ловить эту поломку. При смене path_MainSource_ResolvesUnderMain проверь это утверждение.");
        assertTrue(
                Files.isRegularFile(file),
                "логический путь main-исходника обязан вести к файлу: " + file
                        + " Иначе тест path_MainSource_ResolvesUnderMain перестанет ловить эту поломку. При смене path_MainSource_ResolvesUnderMain проверь это утверждение.");
    }

    @Test
    void path_TestSource_ResolvesUnderTest() {
        Path file = SourceFiles.path("guards/SourceFilesTests.java");

        assertEquals(
                "guards/SourceFilesTests.java",
                SourceFiles.logical(file),
                "логический путь теста обязан пережить оба перевода — где бы ни лежал модуль :guards"
                        + " Иначе тест path_TestSource_ResolvesUnderTest перестанет ловить эту поломку. При смене path_TestSource_ResolvesUnderTest проверь это утверждение.");
        assertTrue(
                Files.isRegularFile(file),
                "логический путь теста обязан вести к файлу: " + file
                        + " Иначе тест path_TestSource_ResolvesUnderTest перестанет ловить эту поломку. При смене path_TestSource_ResolvesUnderTest проверь это утверждение.");
        assertTrue(
                file.normalize().startsWith(SourceFiles.path("guards").normalize()),
                "тест сторожа обязан лежать в каталоге своего модуля: " + file
                        + " Иначе тест path_TestSource_ResolvesUnderTest перестанет ловить эту поломку. При смене path_TestSource_ResolvesUnderTest проверь это утверждение.");
    }

    @Test
    void path_ApiSource_ResolvesUnderApi() {
        Path file = SourceFiles.path("core/observation/BlockCoord.java");

        assertEquals(
                SourceFiles.API.resolve("com/theminespirit/core/observation/BlockCoord.java"),
                file,
                "логический путь модуля :api обязан разворачиваться в API: модуль не зависит от корня"
                        + " Иначе тест path_ApiSource_ResolvesUnderApi перестанет ловить эту поломку. При смене path_ApiSource_ResolvesUnderApi проверь это утверждение.");
        assertTrue(
                Files.isRegularFile(file),
                "логический путь :api обязан вести к файлу: " + file
                        + " Иначе тест path_ApiSource_ResolvesUnderApi перестанет ловить эту поломку. При смене path_ApiSource_ResolvesUnderApi проверь это утверждение.");
    }

    @Test
    void path_Directory_Resolves() {
        assertTrue(
                Files.isDirectory(SourceFiles.path("neoforge/board")),
                "логический путь каталога обязан вести к каталогу"
                        + " Иначе тест path_Directory_Resolves перестанет ловить эту поломку. При смене path_Directory_Resolves проверь это утверждение.");
    }

    @Test
    void path_Unknown_ThrowsWithBothRoots() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SourceFiles.path("core/board/NoSuchFile.java"),
                "неизвестный логический путь обязан падать, а не молча возвращать несуществующий файл"
                        + " Иначе тест path_Unknown_ThrowsWithBothRoots перестанет ловить эту поломку. При смене path_Unknown_ThrowsWithBothRoots проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("core/board/NoSuchFile.java"),
                "ошибка обязана называть логический путь, а не только физический: " + error.getMessage()
                        + " Иначе тест path_Unknown_ThrowsWithBothRoots перестанет ловить эту поломку. При смене path_Unknown_ThrowsWithBothRoots проверь это утверждение.");
        assertTrue(
                error.getMessage().contains("src/main/java")
                        && error.getMessage().contains("src/test/java"),
                "ошибка обязана называть оба корня поиска: " + error.getMessage()
                        + " Иначе тест path_Unknown_ThrowsWithBothRoots перестанет ловить эту поломку. При смене path_Unknown_ThrowsWithBothRoots проверь это утверждение.");
    }

    @Test
    void logical_MainAndTest_RoundTrip() {
        String main = "core/reload/DataTables.java";
        String test = "guards/SourceFilesTests.java";
        String api = "core/observation/BlockCoord.java";

        assertEquals(
                main,
                SourceFiles.logical(SourceFiles.path(main)),
                "логический путь main обязан пережить оба перевода"
                        + " Иначе тест logical_MainAndTest_RoundTrip перестанет ловить эту поломку. При смене logical_MainAndTest_RoundTrip проверь это утверждение.");
        assertEquals(
                test,
                SourceFiles.logical(SourceFiles.path(test)),
                "логический путь теста обязан пережить оба перевода"
                        + " Иначе тест logical_MainAndTest_RoundTrip перестанет ловить эту поломку. При смене logical_MainAndTest_RoundTrip проверь это утверждение.");
        assertEquals(
                api,
                SourceFiles.logical(SourceFiles.path(api)),
                "логический путь :api обязан пережить оба перевода: модуль виден сторожу как часть проекта"
                        + " Иначе тест logical_MainAndTest_RoundTrip перестанет ловить эту поломку. При смене logical_MainAndTest_RoundTrip проверь это утверждение.");
    }

    @Test
    void logical_ForeignFile_StaysNormalized() {
        assertEquals(
                "config/layout.properties",
                SourceFiles.logical(Path.of("config", "layout.properties")),
                "файл вне исходников не должен притворяться логическим путём пакета"
                        + " Иначе тест logical_ForeignFile_StaysNormalized перестанет ловить эту поломку. При смене logical_ForeignFile_StaysNormalized проверь это утверждение.");
    }

    @Test
    void resource_AndTestResource_UseLayoutRoots() {
        assertTrue(
                Files.isDirectory(SourceFiles.resource("data/theminespirit")),
                "логический путь ресурса обязан вести в каталог data/theminespirit"
                        + " Иначе тест resource_AndTestResource_UseLayoutRoots перестанет ловить эту поломку. При смене resource_AndTestResource_UseLayoutRoots проверь это утверждение.");
        assertTrue(
                Files.isDirectory(SourceFiles.testResource("live")),
                "логический путь тестового ресурса обязан вести в каталог live"
                        + " Иначе тест resource_AndTestResource_UseLayoutRoots перестанет ловить эту поломку. При смене resource_AndTestResource_UseLayoutRoots проверь это утверждение.");
    }

    @Test
    void gameTests_HaveOwnModuleRoot_AndExist() {
        assertTrue(
                SourceFiles.GAME_TESTS.startsWith(Path.of("gametest/src/main/java")),
                "GameTest — отдельный модуль :gametest, а не часть main-исходников мода"
                        + " Иначе тест gameTests_HaveOwnModuleRoot_AndExist перестанет ловить эту поломку. При смене gameTests_HaveOwnModuleRoot_AndExist проверь это утверждение.");
        assertTrue(
                Files.isDirectory(SourceFiles.GAME_TESTS),
                "каталог GameTest обязан существовать"
                        + " Иначе тест gameTests_HaveOwnModuleRoot_AndExist перестанет ловить эту поломку. При смене gameTests_HaveOwnModuleRoot_AndExist проверь это утверждение.");
    }
}
