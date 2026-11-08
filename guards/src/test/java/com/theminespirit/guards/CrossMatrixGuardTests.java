package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CrossMatrixGuardTests {
    @Test
    void document_MatrixAndTests_AreConsistent() {
        assertEquals(
                List.of(),
                CrossMatrixDocument.check(),
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
    void detail_MissingGraphFact_IsReported() {
        String text = CrossMatrixDocument.baseDocument().replace("| граф | механика | ОК |", "|  | механика | ОК |");

        assertTrue(
                CrossMatrixDocument.issues(text, Map.of("ATests", "class ATests { void ok() {} }")).stream()
                        .anyMatch(issue -> issue.contains("не говорит, что дал граф")),
                "Сторож ослеп: строка пары без графа обязана попасть в нарушения");
    }
}
