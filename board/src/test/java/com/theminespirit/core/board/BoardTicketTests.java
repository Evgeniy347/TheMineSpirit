package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.board.api.BoardCase;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class BoardTicketTests {
    private static final int FENCE_PRIORITY = 100;
    private static final UUID SPIRIT = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void ticket_Fields_AreRead() {
        BoardTicket ticket = new BoardTicket(
                "fence_1",
                BoardCase.REPAIR_FENCE,
                Optional.of(SPIRIT),
                FENCE_PRIORITY,
                500,
                2,
                "fence:1:64:2",
                List.of("capture_1"),
                Map.of("dirt", 9));
        assertEquals(
                "fence_1",
                ticket.id(),
                "id задания обязан читаться: по нему доска находит задание Иначе тест ticket_Fields_AreRead перестанет ловить эту поломку. При смене ticket_Fields_AreRead проверь это утверждение.");
        assertEquals(
                BoardCase.REPAIR_FENCE,
                ticket.boardCase(),
                "Кейс обязан читаться: по нему доска берёт приоритет и план Иначе тест ticket_Fields_AreRead перестанет ловить эту поломку. При смене ticket_Fields_AreRead проверь это утверждение.");
        assertEquals(
                Optional.of(SPIRIT),
                ticket.assignee(),
                "Владелец-дух обязан читаться: доска проверяет занятость владельца Иначе тест ticket_Fields_AreRead перестанет ловить эту поломку. При смене ticket_Fields_AreRead проверь это утверждение.");
        assertEquals(
                FENCE_PRIORITY,
                ticket.priority(),
                "Приоритет обязан читаться: им доска сортирует очередь Иначе тест ticket_Fields_AreRead перестанет ловить эту поломку. При смене ticket_Fields_AreRead проверь это утверждение.");
        assertEquals(
                500,
                ticket.createdAt(),
                "Момент постановки обязан читаться: им доска сортирует очередь Иначе тест ticket_Fields_AreRead перестанет ловить эту поломку. При смене ticket_Fields_AreRead проверь это утверждение.");
        assertEquals(
                2,
                ticket.level(),
                "Уровень обязан читаться: по нему доска отличает "
                        + "расширение шахты от её входа Иначе тест ticket_Fields_AreRead перестанет ловить эту поломку. При смене ticket_Fields_AreRead проверь это утверждение.");
        assertEquals(
                "fence:1:64:2",
                ticket.cause(),
                "Причина обязана читаться: по ней пересборка проверяет актуальность Иначе тест ticket_Fields_AreRead перестанет ловить эту поломку. При смене ticket_Fields_AreRead проверь это утверждение.");
        assertEquals(
                List.of("capture_1"),
                ticket.dependsOn(),
                "Зависимости обязаны читаться: без них доска не разблокирует задание Иначе тест ticket_Fields_AreRead перестанет ловить эту поломку. При смене ticket_Fields_AreRead проверь это утверждение.");
        assertEquals(
                Map.of("dirt", 9),
                ticket.requiredItems(),
                "Требуемые ресурсы обязаны читаться: ими гейт держит задание в очереди Иначе тест ticket_Fields_AreRead перестанет ловить эту поломку. При смене ticket_Fields_AreRead проверь это утверждение.");
    }

    @Test
    void ticket_EmptyOwner_AnyFreeSpirit() {
        BoardTicket ticket = new BoardTicket(
                "build_1",
                BoardCase.BUILD_HOUSE,
                Optional.empty(),
                50,
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
        assertFalse(
                ticket.assignee().isPresent(),
                "Пустой владелец означает «любой свободный дух»: доска сама назначает "
                        + "исполнителя при выдаче, адресный билет не нужен Иначе тест ticket_EmptyOwner_AnyFreeSpirit перестанет ловить эту поломку. При смене ticket_EmptyOwner_AnyFreeSpirit проверь это утверждение.");
    }

    @Test
    void ticket_EmptyId_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BoardTicket(
                        "",
                        BoardCase.BUILD_HOUSE,
                        Optional.empty(),
                        50,
                        0,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                "Пустой id обязан отвергаться: по id доска находит задание и связывает "
                        + "зависимости, пустое имя слило бы разные задания Иначе тест ticket_EmptyId_Rejected перестанет ловить эту поломку. При смене ticket_EmptyId_Rejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new BoardTicket(
                        "  ",
                        BoardCase.BUILD_HOUSE,
                        Optional.empty(),
                        50,
                        0,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                "id из пробелов обязан отвергаться: он выглядит пустым для наблюдателя, "
                        + "но проходит мимо проверки на пустую строку Иначе тест ticket_EmptyId_Rejected перестанет ловить эту поломку. При смене ticket_EmptyId_Rejected проверь это утверждение.");
    }

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
}
