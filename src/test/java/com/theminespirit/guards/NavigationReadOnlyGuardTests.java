package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class NavigationReadOnlyGuardTests {
    private static final Set<String> MUTATIONS =
            Set.of("setBlock", "destroyBlock", "placeBlock", "removeBlock", "BlockBreaker", "BlockPlacer");
    private static final List<Path> SOURCES = List.of(
            Path.of("src/main/java/com/theminespirit/core/navigation"),
            Path.of("src/main/java/com/theminespirit/neoforge/movement/LevelNavWorld.java"));

    @Test
    void navigationSources_WorldMutationApis_AreAbsent() {
        List<String> offenders = new ArrayList<>();
        for (Path source : SOURCES) {
            for (Path file : javaFiles(source)) {
                String text = read(file);
                for (String mutation : MUTATIONS) {
                    if (text.contains(mutation)) {
                        offenders.add(file + ": " + mutation);
                    }
                }
            }
        }
        assertEquals(
                List.of(),
                offenders,
                "Навигация обязана только читать мир: ломать и ставить блоки по пути запрещено (ARCHITECTURE.md, "
                        + "фундамент). Нарушители");
    }

    private static List<Path> javaFiles(Path root) {
        if (!Files.isDirectory(root)) {
            return List.of(root);
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(file -> file.toString().endsWith(".java")).toList();
        } catch (IOException error) {
            throw new UncheckedIOException("Сторож навигации не смог обойти " + root, error);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException("Сторож навигации не смог прочитать " + file, error);
        }
    }
}
