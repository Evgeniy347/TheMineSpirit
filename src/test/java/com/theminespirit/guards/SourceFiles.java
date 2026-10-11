package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

final class SourceFiles {
    static final Path MAIN = Path.of("src/main/java");
    static final Path TEST = Path.of("src/test/java");
    static final Path GAME_TESTS = Path.of("src/main/java/com/theminespirit/gametest");

    private SourceFiles() {
    }

    static Map<Path, String> javaUnder(Path root) {
        assertTrue(Files.isDirectory(root),
                "Сторож не нашёл каталог " + root + ": тесты запускаются из корня проекта (./gradlew test)");
        try (Stream<Path> files = Files.walk(root)) {
            Map<Path, String> sources = new TreeMap<>();
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                sources.put(file, Files.readString(file));
            }
            return sources;
        } catch (IOException e) {
            throw new UncheckedIOException("Сторож не смог прочитать исходники в " + root, e);
        }
    }

    static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
