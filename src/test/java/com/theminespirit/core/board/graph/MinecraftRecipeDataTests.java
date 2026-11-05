package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MinecraftRecipeDataTests {
    @Test
    void selectJar_PinnedVersionOlderThanOther_ReturnsPinned(@TempDir Path artifacts) throws IOException {
        Path pinned = Files.writeString(artifacts.resolve("minecraft_1.21.1_client.jar"), "pinned");
        Path newest = Files.writeString(artifacts.resolve("minecraft_1.22_client.jar"), "newest");
        Files.setLastModifiedTime(pinned, FileTime.fromMillis(1_000));
        Files.setLastModifiedTime(newest, FileTime.fromMillis(2_000));

        assertEquals(
                pinned,
                MinecraftRecipeData.selectJar(artifacts, "1.21.1"),
                "Нужен jar закреплённой версии Minecraft, а не самый свежий по времени: свежий jar может быть "
                        + "другой версией с другими рецептами, и выгрузка графа поедет");
    }

    @Test
    void selectJar_MissingPinnedVersion_ThrowsWithVersion(@TempDir Path artifacts) throws IOException {
        Files.writeString(artifacts.resolve("minecraft_1.22_client.jar"), "newest");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> MinecraftRecipeData.selectJar(artifacts, "1.21.1"),
                "Отсутствие jar закреплённой версии — ошибка, а не подмена другим jar");
        assertTrue(
                error.getMessage().contains("minecraft_1.21.1_client.jar"),
                "Сообщение обязано называть точный jar закреплённой версии: " + error.getMessage());
    }

    @Test
    void pinnedVersion_GradleProperties_MatchesProjectVersion() throws IOException {
        String expected = Files.readAllLines(Path.of("gradle.properties"), StandardCharsets.UTF_8).stream()
                .filter(line -> line.startsWith("minecraft_version="))
                .map(line -> line.substring("minecraft_version=".length()).trim())
                .findFirst()
                .orElseThrow();

        assertEquals(
                expected,
                MinecraftRecipeData.pinnedVersion(),
                "Версия jar берётся из закреплённой настройки проекта, а не угадывается по кэшу");
    }

    @Test
    void recipes_LoadedFromPinnedJar_ContainVanillaRecipes() {
        List<String> probes = List.of("stick", "crafting_table", "furnace");
        StringBuilder missing = new StringBuilder();
        for (String probe : probes) {
            if (!MinecraftRecipeData.recipes().containsKey(probe)) {
                missing.append(' ').append(probe);
            }
        }
        assertTrue(
                missing.isEmpty(),
                "Из клиентского jar закреплённой версии прочитаны не все ванильные рецепты, нет:" + missing);
    }
}
