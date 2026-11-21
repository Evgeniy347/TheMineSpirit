package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.ui.api.BoardTileGroup;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardListQueryTests {
    @Test
    void parse_EmptyQuery_UsesReasonableDefaults() {
        BoardListQuery query = BoardListQuery.parse(Map.of());

        assertEquals(
                BoardListQuery.DEFAULT_LIMIT,
                query.limit(),
                "Без limit доска обязана отдавать порцию, а не всё"
                        + " Иначе тест parse_EmptyQuery_UsesReasonableDefaults перестанет ловить эту поломку. При смене parse_EmptyQuery_UsesReasonableDefaults проверь это утверждение.");
        assertEquals(
                0,
                query.offset(),
                "Без offset лента обязана начинаться с начала"
                        + " Иначе тест parse_EmptyQuery_UsesReasonableDefaults перестанет ловить эту поломку. При смене parse_EmptyQuery_UsesReasonableDefaults проверь это утверждение.");
        assertNull(
                query.status(),
                "Без status фильтра по статусу нет"
                        + " Иначе тест parse_EmptyQuery_UsesReasonableDefaults перестанет ловить эту поломку. При смене parse_EmptyQuery_UsesReasonableDefaults проверь это утверждение.");
        assertNull(
                query.group(),
                "Без group фильтра по колонке нет"
                        + " Иначе тест parse_EmptyQuery_UsesReasonableDefaults перестанет ловить эту поломку. При смене parse_EmptyQuery_UsesReasonableDefaults проверь это утверждение.");
        assertNull(
                query.assignee(),
                "Без assignee фильтра по духу нет"
                        + " Иначе тест parse_EmptyQuery_UsesReasonableDefaults перестанет ловить эту поломку. При смене parse_EmptyQuery_UsesReasonableDefaults проверь это утверждение.");
        assertNull(
                query.boardCase(),
                "Без case фильтра по кейсу нет"
                        + " Иначе тест parse_EmptyQuery_UsesReasonableDefaults перестанет ловить эту поломку. При смене parse_EmptyQuery_UsesReasonableDefaults проверь это утверждение.");
        assertEquals(
                0L,
                query.since(),
                "Без since ограничения по времени постановки нет"
                        + " Иначе тест parse_EmptyQuery_UsesReasonableDefaults перестанет ловить эту поломку. При смене parse_EmptyQuery_UsesReasonableDefaults проверь это утверждение.");
    }

    @Test
    void parse_AllFilters_KeepsParsedValues() {
        String assignee = UUID.randomUUID().toString();

        BoardListQuery query = BoardListQuery.parse(Map.of(
                "limit", "25",
                "offset", "50",
                "status", "assigned",
                "group", "active",
                "assignee", assignee,
                "case", "build_fence",
                "since", "1234"));

        assertEquals(
                25,
                query.limit(),
                "limit обязан дойти до выборки"
                        + " Иначе тест parse_AllFilters_KeepsParsedValues перестанет ловить эту поломку. При смене parse_AllFilters_KeepsParsedValues проверь это утверждение.");
        assertEquals(
                50,
                query.offset(),
                "offset обязан дойти до выборки"
                        + " Иначе тест parse_AllFilters_KeepsParsedValues перестанет ловить эту поломку. При смене parse_AllFilters_KeepsParsedValues проверь это утверждение.");
        assertEquals(
                BoardStatus.ASSIGNED,
                query.status(),
                "status обязан разбираться без учёта регистра"
                        + " Иначе тест parse_AllFilters_KeepsParsedValues перестанет ловить эту поломку. При смене parse_AllFilters_KeepsParsedValues проверь это утверждение.");
        assertEquals(
                BoardTileGroup.ACTIVE,
                query.group(),
                "group обязан разбираться без учёта регистра"
                        + " Иначе тест parse_AllFilters_KeepsParsedValues перестанет ловить эту поломку. При смене parse_AllFilters_KeepsParsedValues проверь это утверждение.");
        assertEquals(
                assignee,
                query.assignee(),
                "assignee обязан дойти до выборки"
                        + " Иначе тест parse_AllFilters_KeepsParsedValues перестанет ловить эту поломку. При смене parse_AllFilters_KeepsParsedValues проверь это утверждение.");
        assertEquals(
                "BUILD_FENCE",
                query.boardCase(),
                "case обязан дойти до выборки именем BoardCase"
                        + " Иначе тест parse_AllFilters_KeepsParsedValues перестанет ловить эту поломку. При смене parse_AllFilters_KeepsParsedValues проверь это утверждение.");
        assertEquals(
                1234L,
                query.since(),
                "since обязан дойти до выборки"
                        + " Иначе тест parse_AllFilters_KeepsParsedValues перестанет ловить эту поломку. При смене parse_AllFilters_KeepsParsedValues проверь это утверждение.");
    }

    @Test
    void parse_UnknownParameter_IsRejectedWithFieldName() {
        ApiException error = assertThrows(
                ApiException.class,
                () -> BoardListQuery.parse(Map.of("filter", "crater")),
                "Неизвестный параметр обязан быть отвергнут: молчаливое игнорирование врёт клиенту"
                        + " Иначе тест parse_UnknownParameter_IsRejectedWithFieldName перестанет ловить эту поломку. При смене parse_UnknownParameter_IsRejectedWithFieldName проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("filter"),
                "Ошибка обязана назвать неизвестный параметр, а не только «неверный запрос»: " + error.getMessage()
                        + " Иначе тест parse_UnknownParameter_IsRejectedWithFieldName перестанет ловить эту поломку. При смене parse_UnknownParameter_IsRejectedWithFieldName проверь это утверждение.");
    }

    @Test
    void parse_LimitAboveMaximum_IsRejected() {
        ApiException error = assertThrows(
                ApiException.class,
                () -> BoardListQuery.parse(Map.of("limit", "100000")),
                "Слишком большая порция обязана быть отвергнута, иначе сервер снова собирает всю доску"
                        + " При смене parse_LimitAboveMaximum_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("limit"),
                "Ошибка обязана назвать limit и границы: " + error.getMessage()
                        + " Иначе тест parse_LimitAboveMaximum_IsRejected перестанет ловить эту поломку. При смене parse_LimitAboveMaximum_IsRejected проверь это утверждение.");
    }

    @Test
    void parse_NegativeOffset_IsRejected() {
        ApiException error = assertThrows(
                ApiException.class,
                () -> BoardListQuery.parse(Map.of("offset", "-1")),
                "Отрицательное смещение обязано быть отвергнуто: лента не начинается до начала"
                        + " Иначе тест parse_NegativeOffset_IsRejected перестанет ловить эту поломку. При смене parse_NegativeOffset_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("offset"),
                "Ошибка обязана назвать offset: " + error.getMessage()
                        + " Иначе тест parse_NegativeOffset_IsRejected перестанет ловить эту поломку. При смене parse_NegativeOffset_IsRejected проверь это утверждение.");
    }

    @Test
    void parse_BadStatus_NamesAllowedValues() {
        ApiException error = assertThrows(
                ApiException.class,
                () -> BoardListQuery.parse(Map.of("status", "ГОТОВО")),
                "Неизвестный статус обязан быть отвергнут, иначе фильтр молча ничего не вернёт"
                        + " При смене parse_BadStatus_NamesAllowedValues проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("ASSIGNED"),
                "Ошибка обязана перечислять допустимые статусы: " + error.getMessage()
                        + " Иначе тест parse_BadStatus_NamesAllowedValues перестанет ловить эту поломку. При смене parse_BadStatus_NamesAllowedValues проверь это утверждение.");
    }

    @Test
    void parse_BadAssignee_IsRejected() {
        ApiException error = assertThrows(
                ApiException.class,
                () -> BoardListQuery.parse(Map.of("assignee", "Тихон")),
                "assignee — UUID духа, а не имя: иначе фильтр молча не найдёт ни одного задания"
                        + " При смене parse_BadAssignee_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("assignee"),
                "Ошибка обязана назвать assignee: " + error.getMessage()
                        + " Иначе тест parse_BadAssignee_IsRejected перестанет ловить эту поломку. При смене parse_BadAssignee_IsRejected проверь это утверждение.");
    }

    @Test
    void parse_CursorAndOffsetTogether_AreRejected() {
        String cursor = BoardListings.cursorOf(30);

        ApiException error = assertThrows(
                ApiException.class,
                () -> BoardListQuery.parse(Map.of("cursor", cursor, "offset", "30")),
                "Курсор и offset задают одно и то же: два источника смещения обязаны давать ошибку"
                        + " Иначе тест parse_CursorAndOffsetTogether_AreRejected перестанет ловить эту поломку. При смене parse_CursorAndOffsetTogether_AreRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("cursor"),
                "Ошибка обязана объяснить конфликт cursor/offset: " + error.getMessage()
                        + " Иначе тест parse_CursorAndOffsetTogether_AreRejected перестанет ловить эту поломку. При смене parse_CursorAndOffsetTogether_AreRejected проверь это утверждение.");
    }

    @Test
    void parse_Cursor_DecodesIntoOffset() {
        String cursor = BoardListings.cursorOf(60);

        BoardListQuery query = BoardListQuery.parse(Map.of("cursor", cursor));

        assertEquals(
                60,
                query.offset(),
                "Курсор обязан разворачиваться в то же смещение, что вернул сервер"
                        + " Иначе тест parse_Cursor_DecodesIntoOffset перестанет ловить эту поломку. При смене parse_Cursor_DecodesIntoOffset проверь это утверждение.");
    }

    @Test
    void parse_BrokenCursor_IsRejected() {
        ApiException error = assertThrows(
                ApiException.class,
                () -> BoardListQuery.parse(Map.of("cursor", "не-курсор")),
                "Испорченный курсор обязан быть отвергнут, иначе клиент молча получит первую страницу"
                        + " При смене parse_BrokenCursor_IsRejected проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("cursor"),
                "Ошибка обязана назвать cursor: " + error.getMessage()
                        + " Иначе тест parse_BrokenCursor_IsRejected перестанет ловить эту поломку. При смене parse_BrokenCursor_IsRejected проверь это утверждение.");
    }

    @Test
    void parse_SinceAndUpdatedAfter_UsesLaterBoundary() {
        BoardListQuery query = BoardListQuery.parse(Map.of("since", "100", "updated_after", "200"));

        assertEquals(
                200L,
                query.since(),
                "updated_after — синоним since; при двух значениях обязан побеждать поздний"
                        + " Иначе тест parse_SinceAndUpdatedAfter_UsesLaterBoundary перестанет ловить эту поломку. При смене parse_SinceAndUpdatedAfter_UsesLaterBoundary проверь это утверждение.");
    }
}
