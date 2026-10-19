package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EncodingGuardTests {
    private static final Set<String> SKIP_DIRECTORIES = Set.of(
            ".git", ".gradle", ".idea", ".codegraph", "build", "TestResults", "temp-scripts",
            "run", "run-data");
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Test
    void repository_CurrentFiles_AreUtf8WithoutBomAndCorrectLineEndings() {
        List<String> offenders = new ArrayList<>();
        Path root = Path.of(".");

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return dir != root && SKIP_DIRECTORIES.contains(dir.getFileName().toString())
                            ? FileVisitResult.SKIP_SUBTREE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    byte[] bytes = Files.readAllBytes(file);
                    for (String problem : problemsOf(file.getFileName().toString(), bytes)) {
                        offenders.add(root.relativize(file).toString().replace('\\', '/') + " — " + problem);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Сторож не смог обойти дерево проекта: тесты запускаются "
                    + "из корня проекта", e);
        }

        assertEquals(List.of(), offenders,
                "Кодировка и переводы строк ломают сборку и запуск незаметно: BOM в начале исходника "
                        + "ломает javac и shebang, CR в *.sh — bash, не-ASCII или LF в *.bat — командную "
                        + "строку Windows. Исходники и данные — UTF-8 без BOM, *.sh — только LF, *.bat — "
                        + "ASCII и CRLF. Правило: docs/CONVENTIONS.md → «Файлы». Нарушители");
    }

    @Test
    void encoding_BomInSource_IsReported() {
        String text = "\uFEFFclass Sample {}\n";

        assertTrue(problemsOf("Sample.java", text.getBytes(StandardCharsets.UTF_8)).contains("BOM"),
                "Сторож ослеп: BOM в начале файла должен попадать в нарушители");
    }

    @Test
    void encoding_ShellWithCarriageReturn_IsReported() {
        String text = "#!/usr/bin/env bash\r\necho hi\n";

        assertTrue(problemsOf("run.sh", text.getBytes(StandardCharsets.UTF_8))
                        .contains("*.sh содержит CR"),
                "Сторож ослеп: CR в *.sh ломает bash, но нарушение не найдено");
    }

    @Test
    void encoding_BatchWithoutCrlf_IsReported() {
        String text = "@echo off\necho hi\n";

        assertTrue(problemsOf("run.bat", text.getBytes(StandardCharsets.UTF_8))
                        .contains("*.bat не CRLF"),
                "Сторож ослеп: *.bat с LF вместо CRLF ломает командную строку, но нарушение не найдено");
    }

    @Test
    void encoding_BatchWithNonAscii_IsReported() {
        String text = "@echo off\r\nrem привет\r\n";

        assertTrue(problemsOf("run.bat", text.getBytes(StandardCharsets.UTF_8)).contains("*.bat не ASCII"),
                "Сторож ослеп: не-ASCII в *.bat зависит от кодовой страницы Windows, но нарушение не найдено");
    }

    @Test
    void encoding_PlainUtf8WithLf_IsAccepted() {
        String text = "class Sample {\n    String s = \"привет\";\n}\n";

        assertEquals(List.of(), problemsOf("Sample.java", text.getBytes(StandardCharsets.UTF_8)),
                "Сторож ложно срабатывает: UTF-8 без BOM и LF — законный исходник");
    }

    private static List<String> problemsOf(String name, byte[] bytes) {
        List<String> problems = new ArrayList<>();
        if (isBinary(bytes)) {
            return problems;
        }
        if (startsWithBom(bytes)) {
            problems.add("BOM");
        }
        if (!isUtf8(bytes)) {
            problems.add("не UTF-8");
        }
        if (name.endsWith(".sh") && containsCarriageReturn(bytes)) {
            problems.add("*.sh содержит CR");
        }
        if (name.endsWith(".bat")) {
            if (!isAscii(bytes)) {
                problems.add("*.bat не ASCII");
            }
            if (!isCrlf(bytes)) {
                problems.add("*.bat не CRLF");
            }
        }
        return problems;
    }

    private static boolean isBinary(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWithBom(byte[] bytes) {
        if (bytes.length < BOM.length) {
            return false;
        }
        for (int i = 0; i < BOM.length; i++) {
            if (bytes[i] != BOM[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isUtf8(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static boolean containsCarriageReturn(byte[] bytes) {
        for (byte value : bytes) {
            if (value == '\r') {
                return true;
            }
        }
        return false;
    }

    private static boolean isAscii(byte[] bytes) {
        for (byte value : bytes) {
            if (value < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCrlf(byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == '\r' && (i + 1 >= bytes.length || bytes[i + 1] != '\n')) {
                return false;
            }
            if (bytes[i] == '\n' && (i == 0 || bytes[i - 1] != '\r')) {
                return false;
            }
        }
        return true;
    }
}
