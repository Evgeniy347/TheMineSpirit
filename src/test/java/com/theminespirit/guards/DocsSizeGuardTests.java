package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DocsSizeGuardTests {
    private static final int LIMIT = 200;
    private static final List<Path> ROOTS =
            List.of(Path.of("AGENTS.md"), Path.of("README.md"), Path.of("docs"), Path.of("tasks"));

    @Test
    void docs_FilesLongerThanLimit_AreReported() {
        assertEquals(
                List.of(),
                oversized(documents()),
                "Документ длиннее " + LIMIT + " строк: такой файл не читают целиком, правила в нём теряются, "
                        + "а правка требует перечитывать всё. Как надо: раздробить по темам (каталог с README-картой) "
                        + "или сократить без потери смысла. Правило: docs/TESTING.md → «Сторожа», AGENTS.md → «Документы». "
                        + "Нарушители (путь и число строк)");
    }

    @Test
    void oversized_FileOverLimit_IsReported() {
        assertEquals(
                List.of("docs/long.md (201)"),
                oversized(Map.of("docs/long.md", "x\n".repeat(201))),
                "Сторож ослеп: файл длиной 201 строка обязан попасть в нарушители");
    }

    @Test
    void oversized_FileAtLimit_IsAccepted() {
        assertEquals(
                List.of(),
                oversized(Map.of("docs/ok.md", "x\n".repeat(LIMIT))),
                "Сторож ложно срабатывает: файл ровно в предел " + LIMIT + " строк допустим");
    }

    private static Map<String, String> documents() {
        Map<String, String> texts = new TreeMap<>();
        try {
            for (Path root : ROOTS) {
                if (Files.isRegularFile(root)) {
                    texts.put(root.toString(), Files.readString(root, StandardCharsets.UTF_8));
                    continue;
                }
                try (Stream<Path> files = Files.walk(root)) {
                    for (Path file : files.filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".md"))
                            .toList()) {
                        texts.put(file.toString(), Files.readString(file, StandardCharsets.UTF_8));
                    }
                }
            }
            return texts;
        } catch (IOException e) {
            throw new UncheckedIOException("Сторож не смог прочитать документы", e);
        }
    }

    private static List<String> oversized(Map<String, String> texts) {
        List<String> found = new ArrayList<>();
        new TreeMap<>(texts).forEach((path, text) -> {
            long lines = text.lines().count();
            if (lines > LIMIT) {
                found.add(path + " (" + lines + ")");
            }
        });
        return found;
    }
}
