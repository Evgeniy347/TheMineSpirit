package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BoardTextTests {

    private static BoardTask task(Map<String, Integer> missing, List<String> dependsOn, String assignee) {
        return new BoardTask(
                "board-1",
                "SUPPLY_SITE",
                20,
                assignee,
                "WAITING_RESOURCES",
                Map.of("oak_log", 16),
                missing,
                dependsOn,
                7,
                1,
                "always");
    }

    @Test
    void line_WaitingTask_ShowsCasePriorityHolderAndMissing() {
        String line = BoardText.line(task(Map.of("oak_log", 10), List.of("board-root"), "дух-1"));

        assertTrue(line.contains("board-1"), "Строка обязана начинаться с id задания: " + line);
        assertTrue(line.contains("SUPPLY_SITE"), "Строка обязана называть кейс: " + line);
        assertTrue(line.contains("приоритет 20"), "Строка обязана называть приоритет: " + line);
        assertTrue(line.contains("ждёт ресурсов"), "Строка обязана переводить статус на русский: " + line);
        assertTrue(line.contains("дух-1"), "Строка обязана называть владельца: " + line);
        assertTrue(line.contains("oak_log ×10"), "Строка обязана называть нехватку с числом: " + line);
        assertTrue(line.contains("после: board-root"), "Строка обязана называть зависимости: " + line);
    }

    @Test
    void line_FreeTaskWithoutMissing_NamesAnyFreeSpiritAndOmitsMissing() {
        String line = BoardText.line(task(Map.of(), List.of(), null));

        assertTrue(line.contains("любой свободный"), "Задание без владельца обязано звать любого свободного: " + line);
        assertTrue(!line.contains("не хватает"), "Без нехватки строку про ресурсы не показывают: " + line);
        assertTrue(!line.contains("после:"), "Без зависимостей строку про них не показывают: " + line);
    }

    @Test
    void line_LeveledTask_ShowsLevelEffectivePriorityAndModifiers() {
        BoardTask task = new BoardTask(
                "board-2",
                "BUILD_PEN",
                65,
                null,
                "PENDING",
                Map.of(),
                Map.of(),
                List.of(),
                7,
                2,
                "always",
                80,
                List.of("pen_caught_animal"),
                false);

        String line = BoardText.line(task);

        assertTrue(line.contains("уровень 2"), "Строка обязана называть уровень постройки: " + line);
        assertTrue(
                line.contains("приоритет 80"), "Строка обязана показывать эффективный приоритет, а не базу: " + line);
        assertTrue(line.contains("pen_caught_animal"), "Строка обязана называть сработавшие модификаторы: " + line);
    }

    @Test
    void line_DoneBuilding_MarksDone() {
        BoardTask task = new BoardTask(
                "board-3",
                "BUILD_MINE",
                50,
                null,
                "DONE",
                Map.of(),
                Map.of(),
                List.of(),
                7,
                1,
                "always",
                50,
                List.of(),
                true);

        assertTrue(
                BoardText.line(task).contains("выполнено"),
                "Отметка выполненной постройки обязана быть видна в чате: " + BoardText.line(task));
    }

    @Test
    void missing_MultipleItems_SortedByNameForStableChat() {
        assertEquals(
                "dirt ×4, oak_log ×10",
                BoardText.missing(Map.of("oak_log", 10, "dirt", 4)),
                "Нехватка в чате обязана идти по алфавиту: порядок карты непостоянен, а чат читает человек");
    }

    @Test
    void statusRu_KnownStatuses_Translated() {
        assertEquals("выдано", BoardText.statusRu("ASSIGNED"), "Известный статус обязан переводиться на русский");
        assertEquals("выполнено", BoardText.statusRu("DONE"), "Известный статус обязан переводиться на русский");
    }

    @Test
    void statusRu_UnknownWireValue_ReturnedAsIs() {
        assertEquals(
                "САМОПАЛ",
                BoardText.statusRu("САМОПАЛ"),
                "Неизвестный статус не подменяется: наблюдатель видит то, что пришло");
    }
}
