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
                        + "AGENTS.md → «Правило — это сторож или тест». Нарушения"
                        + " Иначе тест document_MatrixAndTests_AreConsistent перестанет ловить эту поломку. При смене document_MatrixAndTests_AreConsistent проверь это утверждение.");
    }

    @Test
    void matrix_MultiPairRow_CoversEveryPair() {
        String text = String.join(
                "\n",
                List.of(
                        "## Реестр механик",
                        "| M01 | a |",
                        "| M02 | b |",
                        "| M03 | c |",
                        "",
                        "## Матрица пар",
                        "| | M01 | M02 | M03 |",
                        "|---|---|---|---|",
                        "| M01 | — | Е | Е |",
                        "| M02 | Е | — | Н |",
                        "| M03 | Е | Н | — |",
                        "",
                        "| пара | тест | граф дал | механика сделала | отметка | сила |",
                        "|---|---|---|---|---|---|",
                        "| 1-2, 1-3 | `ATests.ok` | граф | механика | ОК | полный |"));

        assertEquals(
                List.of(),
                CrossMatrixDocument.issues(text, Map.of("ATests", "class ATests { void ok() {} }")),
                "Строка с несколькими парами через запятую закрывает все перечисленные клетки: иначе матрица "
                        + "требует дублировать один сценарий отдельной строкой на каждую пару"
                        + " При смене matrix_MultiPairRow_CoversEveryPair проверь это утверждение.");
    }

    @Test
    void matrix_AsymmetricCell_IsReported() {
        String text = CrossMatrixDocument.baseDocument().replace("| M01 | — | Н |", "| M01 | — | Е |");

        assertTrue(
                CrossMatrixDocument.issues(text, Map.of("ATests", "class ATests { void ok() {} }")).stream()
                        .anyMatch(issue -> issue.contains("несимметрична")),
                "Сторож ослеп: односторонняя пара (1,2)=Е и (2,1)=Н обязана попасть в нарушения"
                        + " Иначе тест matrix_AsymmetricCell_IsReported перестанет ловить эту поломку. При смене matrix_AsymmetricCell_IsReported проверь это утверждение.");
    }

    @Test
    void detail_MissingMethod_IsReported() {
        String text = CrossMatrixDocument.baseDocument().replace("`ATests.ok`", "`ATests.gone`");

        assertTrue(
                CrossMatrixDocument.issues(text, Map.of("ATests", "class ATests { void ok() {} }")).stream()
                        .anyMatch(issue -> issue.contains("нет метода gone")),
                "Сторож ослеп: ссылка на удалённый метод теста обязана попасть в нарушения"
                        + " Иначе тест detail_MissingMethod_IsReported перестанет ловить эту поломку. При смене detail_MissingMethod_IsReported проверь это утверждение.");
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
                        + CrossMatrixDocument.issues(text, Map.of("ATests", "class ATests { void ok() {} }"))
                        + " Иначе тест matrix_MissingDetailRow_IsReported перестанет ловить эту поломку. При смене matrix_MissingDetailRow_IsReported проверь это утверждение.");
    }

    @Test
    void matrix_NoPairWithoutReason_IsReported() {
        String text = CrossMatrixDocument.baseDocument()
                .replace("| M01 | — | Н |", "| M01 | — | н/п |")
                .replace("| M02 | Н | — |", "| M02 | н/п | — |");

        assertTrue(
                CrossMatrixNoPair.issues(text, "").stream()
                        .anyMatch(issue -> issue.contains("клетка н/п") && issue.contains("без обоснования")),
                "Сторож ослеп: клетка н/п без строки обоснования обязана попасть в нарушения"
                        + " Иначе тест matrix_NoPairWithoutReason_IsReported перестанет ловить эту поломку. При смене matrix_NoPairWithoutReason_IsReported проверь это утверждение.");
    }

    @Test
    void matrix_NoPairReasonForTestedPair_IsReported() {
        String text = CrossMatrixDocument.baseDocument();

        assertTrue(
                CrossMatrixNoPair.issues(text, "| 1-2 | причина достаточной длины |\n").stream()
                        .anyMatch(issue -> issue.contains("обоснование относится к другой паре")),
                "Сторож ослеп: обоснование н/п для пары с общим тестом обязано попасть в нарушения"
                        + " Иначе тест matrix_NoPairReasonForTestedPair_IsReported перестанет ловить эту поломку. При смене matrix_NoPairReasonForTestedPair_IsReported проверь это утверждение.");
    }

    @Test
    void detail_MissingStrength_IsReported() {
        String text = CrossMatrixDocument.baseDocument().replace("| ОК | полный |", "| ОК |  |");

        assertTrue(
                CrossMatrixDocument.issues(text, Map.of("ATests", "class ATests { void ok() {} }")).stream()
                        .anyMatch(issue -> issue.contains("не размечена силой")),
                "Сторож ослеп: строка пары без силы полный/слабо обязана попасть в нарушения"
                        + " Иначе тест detail_MissingStrength_IsReported перестанет ловить эту поломку. При смене detail_MissingStrength_IsReported проверь это утверждение.");
    }

    @Test
    void detail_WeakStrength_IsAccepted() {
        String text = CrossMatrixDocument.baseDocument().replace("| ОК | полный |", "| ОК | слабо |");

        assertTrue(
                CrossMatrixDocument.issues(text, Map.of("ATests", "class ATests { void ok() {} }")).stream()
                        .noneMatch(issue -> issue.contains("силой")),
                "Сторож ложно срабатывает: отметка «слабо» — законная сила пары"
                        + " Иначе тест detail_WeakStrength_IsAccepted перестанет ловить эту поломку. При смене detail_WeakStrength_IsAccepted проверь это утверждение.");
    }

    @Test
    void detail_MissingGraphFact_IsReported() {
        String text = CrossMatrixDocument.baseDocument().replace("| граф | механика | ОК |", "|  | механика | ОК |");

        assertTrue(
                CrossMatrixDocument.issues(text, Map.of("ATests", "class ATests { void ok() {} }")).stream()
                        .anyMatch(issue -> issue.contains("не говорит, что дал граф")),
                "Сторож ослеп: строка пары без графа обязана попасть в нарушения"
                        + " Иначе тест detail_MissingGraphFact_IsReported перестанет ловить эту поломку. При смене detail_MissingGraphFact_IsReported проверь это утверждение.");
    }

    @Test
    void interrupt_OkWithoutTest_IsReported() {
        String text = interruptDocument().replace("`ATests.ok`", "—");

        assertTrue(
                CrossMatrixInterrupt.issues(text, Map.of("ATests", "class ATests { void ok() {} }")).stream()
                        .anyMatch(issue -> issue.contains("без существующего теста")),
                "Сторож ослеп: отметка ОК без теста обязана попасть в нарушения оси прерывания"
                        + " Иначе тест interrupt_OkWithoutTest_IsReported перестанет ловить эту поломку. При смене interrupt_OkWithoutTest_IsReported проверь это утверждение.");
    }

    @Test
    void interrupt_UnknownMechanic_IsReported() {
        String text = interruptDocument().replace("| I01 | столб |", "| I01 | лес |");

        assertTrue(
                CrossMatrixInterrupt.issues(text, Map.of("ATests", "class ATests { void ok() {} }")).stream()
                        .anyMatch(issue -> issue.contains("вне столб/мост/расчистка/шахта/dig_out/маршрут")),
                "Сторож ослеп: механика вне пяти обязательных обязана попасть в нарушения"
                        + " Иначе тест interrupt_UnknownMechanic_IsReported перестанет ловить эту поломку. При смене interrupt_UnknownMechanic_IsReported проверь это утверждение.");
    }

    @Test
    void interrupt_BrokenPairWithoutTask_IsReported() {
        String text = interruptDocument().replace("| Н | задача мост |", "| Н |  |");

        assertTrue(
                CrossMatrixInterrupt.issues(text, Map.of("ATests", "class ATests { void ok() {} }")).stream()
                        .anyMatch(issue -> issue.contains("Н без задачи починки")),
                "Сторож ослеп: клетка Н без задачи починки обязана попасть в нарушения"
                        + " Иначе тест interrupt_BrokenPairWithoutTask_IsReported перестанет ловить эту поломку. При смене interrupt_BrokenPairWithoutTask_IsReported проверь это утверждение.");
    }

    @Test
    void interrupt_FewerThanMinimumRows_IsReported() {
        assertTrue(
                CrossMatrixInterrupt.issues("""
                                | id | механика | прерывание | что остаётся | видит ли граф | отметка | тест |
                                |---|---|---|---|---|---|---|
                                | I01 | столб | голод | след | да | ОК | `ATests.ok` |
                                """, Map.of("ATests", "class ATests { void ok() {} }")).stream()
                        .anyMatch(issue -> issue.contains("меньше 25")),
                "Сторож ослеп: недобор пар первой партии обязана попасть в нарушения"
                        + " Иначе тест interrupt_FewerThanMinimumRows_IsReported перестанет ловить эту поломку. При смене interrupt_FewerThanMinimumRows_IsReported проверь это утверждение.");
    }

    private static String interruptDocument() {
        StringBuilder text = new StringBuilder("""
                | id | механика | прерывание | что остаётся | видит ли граф | отметка | тест или задача |
                |---|---|---|---|---|---|---|
                """);
        for (int index = 1; index <= CrossMatrixInterrupt.MIN; index++) {
            text.append("| I")
                    .append(index < 10 ? "0" + index : Integer.toString(index))
                    .append(" | столб | голод | след | да | ОК | `ATests.ok` |\n");
        }
        text.append("| I99 | мост | ночь | настил | нет | Н | задача мост |\n");
        return text.toString();
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
