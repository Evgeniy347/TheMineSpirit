package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CrossMatrixGuardTests {
    @Test
    void document_MatrixAndTests_AreConsistent() {
        assertEquals(
                List.of(),
                CrossMatrixDocument.check(testSources()),
                "Матрица пересечений противоречит себе или ссылается на несуществующий тест: по такой таблице "
                        + "нельзя поручить доработку, а «есть» оказывается выдумкой. Как надо: реестр и шапка "
                        + "матрицы называют одни M01…M24, клетка Е имеет строку в таблице пар, тест — "
                        + "существующий класс и метод. Правило: docs/testing/CROSS-MATRIX.md, "
                        + "AGENTS.md → «Правило — это сторож или тест». Нарушения");
    }

    @Test
    void matrix_AsymmetricCell_IsReported() {
        String text = CrossMatrixDocument.baseDocument().replace("| M01 | — | Н |", "| M01 | — | Е |");

        assertTrue(
                CrossMatrixDocument.issues(text, Map.of("ATests", "class ATests { void ok() {} }")).stream()
                        .anyMatch(issue -> issue.contains("несимметрична")),
                "Сторож ослеп: односторонняя пара (1,2)=Е и (2,1)=Н обязана попасть в нарушения");
    }

    @Test
    void detail_MissingMethod_IsReported() {
        String text = CrossMatrixDocument.baseDocument().replace("`ATests.ok`", "`ATests.gone`");

        assertTrue(
                CrossMatrixDocument.issues(text, Map.of("ATests", "class ATests { void ok() {} }")).stream()
                        .anyMatch(issue -> issue.contains("нет метода gone")),
                "Сторож ослеп: ссылка на удалённый метод теста обязана попасть в нарушения");
    }

    @Test
    void matrix_MissingDetailRow_IsReported() {
        String text = CrossMatrixDocument.baseDocument()
                .replace("| M01 | — | Н |", "| M01 | — | Е |")
                .replace("| 1-2 | `ATests.ok`", "| 1-3 | `ATests.ok`");

        assertTrue(
                CrossMatrixDocument.issues(text, Map.of("ATests", "class ATests { void ok() {} }")).stream()
                        .anyMatch(issue -> issue.contains("клетка Е пары 1-2 без строки")),
                "Сторож ослеп: клетка Е без строки «граф/механика» обязана попасть в нарушения: "
                        + CrossMatrixDocument.issues(text, Map.of("ATests", "class ATests { void ok() {} }")));
    }

    @Test
    void detail_MissingGraphFact_IsReported() {
        String text = CrossMatrixDocument.baseDocument().replace("| граф | механика | ОК |", "|  | механика | ОК |");

        assertTrue(
                CrossMatrixDocument.issues(text, Map.of("ATests", "class ATests { void ok() {} }")).stream()
                        .anyMatch(issue -> issue.contains("не говорит, что дал граф")),
                "Сторож ослеп: строка пары без графа обязана попасть в нарушения");
    }

    private static Map<String, String> testSources() {
        Map<String, String> sources = new TreeMap<>();
        for (Path root : SourceFiles.moduleTestRoots()) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith("Tests.java"))
                        .toList()) {
                    String name = file.getFileName().toString().replace(".java", "");
                    sources.put(name, Files.readString(file, StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Сторож не смог собрать тесты из " + root, e);
            }
        }
        return sources;
    }
}
