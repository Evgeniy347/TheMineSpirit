package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class SourceFiles {
    public static final Path MAIN = Path.of("src/main/java");
    public static final Path TEST = Path.of("src/test/java");
    public static final Path GAME_TESTS = Path.of("src/main/java/com/theminespirit/gametest");

    private static final Map<Path, Map<Path, String>> CACHE = new ConcurrentHashMap<>();
    private static final Pattern TEXT_BLOCK = Pattern.compile("\"\"\"[ \\t]*\\n(?s:.*?)(?<!\\\\)\"\"\"");

    private SourceFiles() {}

    public static Map<Path, String> javaUnder(Path root) {
        assertTrue(
                Files.isDirectory(root),
                "Сторож не нашёл каталог " + root + ": тесты запускаются из корня проекта (./gradlew test)");
        return CACHE.computeIfAbsent(root, SourceFiles::readJava);
    }

    private static Map<Path, String> readJava(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            TreeMap<Path, String> sources = new TreeMap<>();
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).toList()) {
                sources.put(file, Files.readString(file));
            }
            return Collections.unmodifiableSortedMap(sources);
        } catch (IOException e) {
            throw new UncheckedIOException("Сторож не смог прочитать исходники в " + root, e);
        }
    }

    public static String withoutTextBlocks(String text) {
        Matcher matcher = TEXT_BLOCK.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String newlines = matcher.group().replaceAll("[^\\n]", "");
            matcher.appendReplacement(out, Matcher.quoteReplacement("\"\"" + newlines));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public static int lineOf(String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }
}
