package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BoardTextTests {

    private static BoardTask task(Map<String, Integer> missing, List<String> dependsOn, String assignee) {
        return new BoardTask("board-1", "SUPPLY_SITE", 20, assignee, "WAITING_RESOURCES",
                Map.of("oak_log", 16), missing, dependsOn, 7, 6000);
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
    void missing_MultipleItems_SortedByNameForStableChat() {
        assertEquals("dirt ×4, oak_log ×10", BoardText.missing(Map.of("oak_log", 10, "dirt", 4)),
                "Нехватка в чате обязана идти по алфавиту: порядок карты непостоянен, а чат читает человек");
    }

    @Test
    void statusRu_KnownStatuses_Translated() {
        assertEquals("выдано", BoardText.statusRu("ASSIGNED"),
                "Известный статус обязан переводиться на русский");
        assertEquals("выполнено", BoardText.statusRu("DONE"),
                "Известный статус обязан переводиться на русский");
    }

    @Test
    void statusRu_UnknownWireValue_ReturnedAsIs() {
        assertEquals("САМОПАЛ", BoardText.statusRu("САМОПАЛ"),
                "Неизвестный статус не подменяется: наблюдатель видит то, что пришло");
    }
}
