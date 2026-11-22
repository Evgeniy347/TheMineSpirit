package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.board.api.BoardCase;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardTaskRequestTitleTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static BoardTaskRequest request(String title) {
        return new BoardTaskRequest(
                UUID.randomUUID(),
                BoardCase.ACTIVITY_EXTERNAL,
                Optional.empty(),
                Map.of(),
                List.of(),
                1,
                "always",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                title);
    }

    private static JsonNode node(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    @Test
    void title_ParsedText_TrimmedAndBlankNull() throws Exception {
        assertEquals(
                "Поход за деревом",
                BoardTaskFields.title(node("\"  Поход за деревом  \"")),
                "Название задания обязано обрезаться по краям: иначе пробелы попадают на плитку доски и в UI, "
                        + "и пользователь видит кривое имя вместо названия. При смене разбора title в "
                        + "BoardTaskFields проверь обрезку");
        assertNull(
                BoardTaskFields.title(node("\"   \"")),
                "Пустое название обязано стать null, иначе плитка рисует пустую строку и теряет подпись "
                        + "кейса. При смене разбора title в BoardTaskFields проверь пробельное значение");
        assertNull(
                BoardTaskFields.title(null),
                "Отсутствующее поле title обязано читаться как null: иначе ручное задание без названия не "
                        + "принимается. При смене разбора title в BoardTaskFields проверь null-узел");
    }

    @Test
    void title_NonText_RejectedWithFieldName() throws Exception {
        ApiException rejected = assertThrows(
                ApiException.class,
                () -> BoardTaskFields.title(node("5")),
                "Число вместо строки обязано отклоняться, иначе название молча теряется и задание приходит "
                        + "без имени. При смене проверки типа title в BoardTaskFields проверь не-строку");

        assertTrue(
                rejected.error().message().contains("'title'"),
                "Отказ обязан называть поле title: иначе автор задания ищет опечатку наугад. При смене "
                        + "проверки типа title в BoardTaskFields проверь имя поля в сообщении");
    }

    @Test
    void constructor_LongTitle_RejectedAndBlankNormalized() {
        String longTitle = "а".repeat(BoardTaskRequest.MAX_TITLE_LENGTH + 1);
        ApiException rejected = assertThrows(
                ApiException.class,
                () -> request(longTitle),
                "Название длиннее 100 символов обязано отклоняться, иначе доска получает простыню вместо "
                        + "имени задания. При смене лимита MAX_TITLE_LENGTH в BoardTaskRequest проверь отказ");
        assertTrue(
                rejected.error().message().contains("длиннее " + BoardTaskRequest.MAX_TITLE_LENGTH),
                "Отказ обязан называть предел: иначе автор задания не понимает, насколько сократить название, "
                        + "и повторяет тот же запрос. При смене текста отказа о длине title в BoardTaskRequest "
                        + "проверь число");

        assertNull(
                request("   ").title(),
                "Пробельное название обязано нормализоваться в null на входе, иначе пустая подпись доходит "
                        + "до доски и UI. При смене нормализации title в конструкторе BoardTaskRequest "
                        + "проверь пробельное значение");
    }
}
