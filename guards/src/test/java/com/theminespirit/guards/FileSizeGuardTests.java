package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class FileSizeGuardTests {
    private static final int LIMIT = 300;
    private static final Path BASELINE = Path.of("config", "filesize-baseline.txt");

    @Test
    void javaFiles_LongerThanLimit_AreReported() {
        assertEquals(
                List.of(),
                problems(sizes(), baseline()),
                "Файл кода длиннее " + LIMIT + " строк держит несколько зон ответственности: его трудно читать "
                        + "и менять, правка одной зоны задевает соседние. Как надо: либо раздели логику файла "
                        + "на классы по зонам ответственности (docs/CONVENTIONS.md) в этой же задаче, либо, если "
                        + "разделение — отдельная крупная работа, выпиши техдолг, сообщи владельцу и не трогай "
                        + "файл сверх задачи. Ужимать формат и выжимать строки ради лимита запрещено: лимит не "
                        + "про строки, а про зоны. Новый файл сверх предела сразу красный; файл из "
                        + "config/filesize-baseline.txt не растёт, исправленный — убирается из списка. "
                        + "Нарушители (путь, строк):");
    }

    @Test
    void oversized_NewFileOverLimit_IsReported() {
        assertEquals(
                List.of("Long.java (301): новый файл длиннее " + LIMIT + " строк"),
                problems(Map.of("Long.java", 301), Map.of()),
                "Сторож ослеп: новый файл длиной 301 строка обязан попасть в нарушители");
    }

    @Test
    void oversized_FileAtLimit_IsAccepted() {
        assertEquals(
                List.of(),
                problems(Map.of("Ok.java", LIMIT), Map.of()),
                "Сторож ложно срабатывает: файл ровно в предел " + LIMIT + " строк допустим");
    }

    @Test
    void baseline_RecordedSize_IsAccepted() {
        assertEquals(
                List.of(),
                problems(Map.of("a/Long.java", 320), Map.of("a/Long.java", 320)),
                "Сторож ложно срабатывает: файл из списка исключений с тем же размером допустим");
    }

    @Test
    void baseline_FileGrew_IsReported() {
        assertEquals(
                List.of("a/Long.java (321): вырос с 320 — раздели по зонам или выпиши техдолг владельцу;"
                        + " строки ради лимита не жать"),
                problems(Map.of("a/Long.java", 321), Map.of("a/Long.java", 320)),
                "Сторож ослеп: файл из списка исключений не имеет права расти");
    }

    @Test
    void baseline_FileShrankButStillOversized_IsReported() {
        assertEquals(
                List.of("a/Long.java (310): уменьшился с 320 — запиши новый размер в списке"),
                problems(Map.of("a/Long.java", 310), Map.of("a/Long.java", 320)),
                "Сторож ослеп: уменьшившийся файл обязан обновить свой размер в списке");
    }

    @Test
    void baseline_FileFixed_IsReportedAsStale() {
        assertEquals(
                List.of("a/Long.java (300): исправлен — убери запись из списка"),
                problems(Map.of("a/Long.java", 300), Map.of("a/Long.java", 320)),
                "Сторож ослеп: исправленный файл обязан уйти из списка исключений");
    }

    private static Map<String, Integer> sizes() {
        Map<String, Integer> all = new TreeMap<>();
        for (Path root : SourceFiles.sourceRoots()) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            SourceFiles.javaUnder(root)
                    .forEach((file, text) -> all.put(
                            SourceFiles.logical(file), (int) text.lines().count()));
        }
        return all;
    }

    private static Map<String, Integer> baseline() {
        Map<String, Integer> entries = new TreeMap<>();
        try {
            for (String raw : Files.readAllLines(BASELINE, StandardCharsets.UTF_8)) {
                if (raw.isBlank()) {
                    continue;
                }
                int separator = raw.indexOf('\t');
                assertTrue(
                        separator > 0,
                        "Строка списка исключений " + BASELINE + " должна быть «путь<TAB>строк», а не: " + raw);
                entries.put(
                        raw.substring(0, separator).trim(),
                        Integer.parseInt(raw.substring(separator + 1).trim()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Сторож не смог прочитать " + BASELINE, e);
        }
        return entries;
    }

    private static List<String> problems(Map<String, Integer> sizes, Map<String, Integer> baseline) {
        List<String> found = new ArrayList<>();
        new TreeMap<>(sizes).forEach((path, lines) -> {
            if (lines <= LIMIT) {
                return;
            }
            Integer was = baseline.get(path);
            if (was == null) {
                found.add(path + " (" + lines + "): новый файл длиннее " + LIMIT + " строк");
            } else if (lines > was) {
                found.add(path + " (" + lines + "): вырос с " + was
                        + " — раздели по зонам или выпиши техдолг владельцу; строки ради лимита не жать");
            } else if (lines < was) {
                found.add(path + " (" + lines + "): уменьшился с " + was + " — запиши новый размер в списке");
            }
        });
        new TreeMap<>(baseline).forEach((path, was) -> {
            Integer lines = sizes.get(path);
            if (lines == null) {
                found.add(path + " (" + was + "): файла нет — убери запись из списка");
            } else if (lines <= LIMIT) {
                found.add(path + " (" + lines + "): исправлен — убери запись из списка");
            }
        });
        return found;
    }
}
