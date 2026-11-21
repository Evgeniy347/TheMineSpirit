package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class BoardTicketPart2Tests {
    @Provide
    Arbitrary<String> ticketIds() {
        return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(1).ofMaxLength(12);
    }

    @Provide
    Arbitrary<BoardCase> boardCases() {
        return Arbitraries.of(BoardCase.values());
    }

    @Provide
    Arbitrary<Integer> levels() {
        return Arbitraries.integers().between(1, 4);
    }

    @Provide
    Arbitrary<String> causes() {
        return Arbitraries.of("always", "crater:1:64:2", "fence:3:64:1", "animal:capture_1");
    }

    @Test
    void ticket_NullCase_Rejected() {
        assertThrows(
                NullPointerException.class,
                () -> new BoardTicket(
                        "build_1", null, Optional.empty(), 50, 0, 1, BoardTicket.ALWAYS_CAUSE, List.of(), Map.of()),
                "Кейс обязан быть задан: без него доска не знает приоритет, гейт и план Иначе тест ticket_NullCase_Rejected перестанет ловить эту поломку. При смене ticket_NullCase_Rejected проверь это утверждение.");
    }

    @Test
    void ticket_NegativeCreatedAt_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BoardTicket(
                        "build_1",
                        BoardCase.BUILD_HOUSE,
                        Optional.empty(),
                        50,
                        -1,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                "Отрицательный момент постановки обязан отвергаться: время до начала отсчёта "
                        + "сломает сортировку по createdAt При смене ticket_NegativeCreatedAt_Rejected проверь это утверждение.");
    }

    @Test
    void ticket_ZeroLevel_Rejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new BoardTicket(
                        "build_1",
                        BoardCase.BUILD_HOUSE,
                        Optional.empty(),
                        50,
                        0,
                        0,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                "Нулевой уровень обязан отвергаться: первая постройка ставится уровнем 1, "
                        + "нулевой уровень снял бы условие «уровень 1 готов» с доски Иначе тест ticket_ZeroLevel_Rejected перестанет ловить эту поломку. При смене ticket_ZeroLevel_Rejected проверь это утверждение.");
        assertTrue(
                error.getMessage().contains("1"),
                "Сообщение обязано называть минимальный уровень:  Иначе тест ticket_ZeroLevel_Rejected перестанет ловить эту поломку. При смене ticket_ZeroLevel_Rejected проверь это утверждение."
                        + error.getMessage());
        assertThrows(
                IllegalArgumentException.class,
                () -> new BoardTicket(
                        "build_1",
                        BoardCase.BUILD_HOUSE,
                        Optional.empty(),
                        50,
                        0,
                        -2,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                "Отрицательный уровень обязан отвергаться: уровень ниже первого ломает "
                        + "цепочку уровней постройки При смене ticket_ZeroLevel_Rejected проверь это утверждение.");
    }

    @Test
    void ticket_BlankCause_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BoardTicket(
                        "build_1", BoardCase.BUILD_HOUSE, Optional.empty(), 50, 0, 1, null, List.of(), Map.of()),
                "Причина null обязана отвергаться: без причины пересборка не проверит "
                        + "актуальность, и задание повиснет после отпадения причины Иначе тест ticket_BlankCause_Rejected перестанет ловить эту поломку. При смене ticket_BlankCause_Rejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new BoardTicket(
                        "build_1", BoardCase.BUILD_HOUSE, Optional.empty(), 50, 0, 1, "  ", List.of(), Map.of()),
                "Причина из пробелов обязана отвергаться: она не совпадёт ни с одной живой "
                        + "причиной, и задание снимут на первой же пересборке Иначе тест ticket_BlankCause_Rejected перестанет ловить эту поломку. При смене ticket_BlankCause_Rejected проверь это утверждение.");
    }

    @Test
    void ticket_AlwaysCause_MarksEvergreen() {
        BoardTicket ticket = new BoardTicket(
                "eat_1",
                BoardCase.EAT_TO_FULL,
                Optional.empty(),
                80,
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
        assertEquals(
                "always",
                ticket.cause(),
                "Постоянное задание задают причиной always: такая причина переживает снятие "
                        + "по актуальности, иначе вечное задание умрёт на первой пересборке При смене ticket_AlwaysCause_MarksEvergreen проверь это утверждение.");
    }
}
