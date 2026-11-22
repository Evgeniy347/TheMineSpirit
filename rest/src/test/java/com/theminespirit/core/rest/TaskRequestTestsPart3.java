package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TaskRequestTestsPart3 {
    private static final String UUID_TEXT = "3f1c2d3e-0000-4000-8000-000000000000";

    private static TaskRequest parse(String extra) {
        String json = "{\"request_id\":\"" + UUID_TEXT + "\",\"goal\":\"цель\"" + extra + "}";
        return TaskRequest.parse(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void title_PresentText_TrimmedIntoTicketName() {
        TaskRequest parsed = parse(",\"title\":\"  Поход за деревом  \"");

        assertEquals(
                "Поход за деревом",
                parsed.title(),
                "Название задачи обязано обрезаться по краям: пробелы вокруг названия не должны попадать на "
                        + "доску и в UI, иначе пользователь видит кривую плитку, а поиск по title не находит "
                        + "задачу. При смене разбора title в TaskRequest проверь обрезку и лимит 100");
    }

    @Test
    void title_BlankText_BecomesNull() {
        assertNull(
                parse(",\"title\":\"   \"").title(),
                "Пустое название обязано стать null, иначе плитка рисует пустую строку вместо имени кейса и "
                        + "пользователь не понимает, что дух делает. При смене нормализации пустого title в "
                        + "TaskRequest проверь отсутствие и пробельное значение");
        assertNull(
                parse("").title(),
                "Без поля title задача остаётся без названия, иначе появится случайная подпись и прежняя "
                        + "подпись кейса пропадёт. При смене нормализации отсутствующего title в TaskRequest "
                        + "проверь поведение без поля");
    }

    @Test
    void title_LongerThanLimit_RejectedWithClearReason() {
        String longTitle = "а".repeat(TaskRequest.MAX_TITLE_LENGTH + 1);

        ApiException rejected = assertThrows(
                ApiException.class,
                () -> parse(",\"title\":\"" + longTitle + "\""),
                "Название длиннее 100 символов обязано отклоняться, иначе доска и журнал получают простыню "
                        + "вместо имени задания. При смене лимита MAX_TITLE_LENGTH в TaskRequest проверь "
                        + "границу и текст отказа");

        assertTrue(
                rejected.error().message().contains("длиннее " + TaskRequest.MAX_TITLE_LENGTH),
                "Отказ обязан называть предел: иначе автор задачи не понимает, насколько сократить название, "
                        + "и повторяет тот же запрос. При смене текста отказа о длине title в "
                        + "TaskRequest проверь число в сообщении");
    }

    @Test
    void title_NotText_RejectedWithFieldName() {
        ApiException rejected = assertThrows(
                ApiException.class,
                () -> parse(",\"title\":5"),
                "Число вместо строки обязано отклоняться, иначе название молча теряется и задача приходит "
                        + "без имени. При смене проверки типа title в TaskRequest проверь отказ на не-строку");

        assertTrue(
                rejected.error().message().contains("'title'"),
                "Отказ обязан называть поле title: иначе автор задачи ищет опечатку наугад и повторяет тот "
                        + "же запрос. При смене проверки типа title в TaskRequest проверь имя поля в сообщении");
    }
}
