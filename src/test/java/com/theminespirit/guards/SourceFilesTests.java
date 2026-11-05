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
        Path file = SourceFiles.path("core/board/Board.java");

        assertEquals(
                SourceFiles.MAIN.resolve("com/theminespirit/core/board/Board.java"),
                file,
                "логический путь main-исходника обязан разворачиваться в MAIN, а не в TEST");
        assertTrue(Files.isRegularFile(file), "логический путь main-исходника обязан вести к файлу: " + file);
    }

    @Test
    void path_TestSource_ResolvesUnderTest() {
        Path file = SourceFiles.path("core/board/BoardCaseTests.java");

        assertEquals(
                SourceFiles.TEST.resolve("com/theminespirit/core/board/BoardCaseTests.java"),
                file,
                "логический путь теста обязан разворачиваться в TEST, а не в MAIN");
        assertTrue(Files.isRegularFile(file), "логический путь теста обязан вести к файлу: " + file);
    }

    @Test
    void path_Directory_Resolves() {
        assertTrue(
                Files.isDirectory(SourceFiles.path("neoforge/board")),
                "логический путь каталога обязан вести к каталогу");
    }

    @Test
    void path_Unknown_ThrowsWithBothRoots() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SourceFiles.path("core/board/NoSuchFile.java"),
                "неизвестный логический путь обязан падать, а не молча возвращать несуществующий файл");

        assertTrue(
                error.getMessage().contains("core/board/NoSuchFile.java"),
                "ошибка обязана называть логический путь, а не только физический: " + error.getMessage());
        assertTrue(
                error.getMessage().contains("src/main/java")
                        && error.getMessage().contains("src/test/java"),
                "ошибка обязана называть оба корня поиска: " + error.getMessage());
    }

    @Test
    void logical_MainAndTest_RoundTrip() {
        String main = "core/board/Board.java";
        String test = "core/board/BoardCaseTests.java";

        assertEquals(
                main, SourceFiles.logical(SourceFiles.path(main)), "логический путь main обязан пережить оба перевода");
        assertEquals(
                test,
                SourceFiles.logical(SourceFiles.path(test)),
                "логический путь теста обязан пережить оба перевода");
    }

    @Test
    void logical_ForeignFile_StaysNormalized() {
        assertEquals(
                "config/layout.properties",
                SourceFiles.logical(Path.of("config", "layout.properties")),
                "файл вне исходников не должен притворяться логическим путём пакета");
    }

    @Test
    void resource_AndTestResource_UseLayoutRoots() {
        assertTrue(
                Files.isDirectory(SourceFiles.resource("data/theminespirit")),
                "логический путь ресурса обязан вести в каталог data/theminespirit");
        assertTrue(
                Files.isDirectory(SourceFiles.testResource("live")),
                "логический путь тестового ресурса обязан вести в каталог live");
    }

    @Test
    void gameTests_UnderMainRoot_AreDirectories() {
        assertTrue(SourceFiles.GAME_TESTS.startsWith(SourceFiles.MAIN), "GameTest — часть main-исходников");
        assertTrue(Files.isDirectory(SourceFiles.GAME_TESTS), "каталог GameTest обязан существовать");
    }
}
