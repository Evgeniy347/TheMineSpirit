package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvergreenRulesTests {

    private static final UUID SPIRIT_A = UUID.randomUUID();
    private static final UUID SPIRIT_B = UUID.randomUUID();
    private static final EatNeed HUNGRY = new EatNeed(10, 20, 20, 20);
    private static final EatNeed FULL = new EatNeed(20, 20, 20, 20);
    private static final SpiritView FREE = SpiritView.free();

    @Test
    void issuedTo_AssignedApplicableSpirit_Issued() {
        BoardTicket assigned = ticket("eat_a", BoardCase.EAT_TO_FULL, Optional.of(SPIRIT_A));

        assertTrue(
                EvergreenRules.issuedTo(assigned, SPIRIT_A, HUNGRY, FREE),
                "Адресное задание голодному духу-владельцу выдаётся: он применим и это его задание"
                        + " Иначе тест issuedTo_AssignedApplicableSpirit_Issued перестанет ловить эту поломку. При смене issuedTo_AssignedApplicableSpirit_Issued проверь это утверждение.");
    }

    @Test
    void issuedTo_AssignedOtherSpirit_NotIssued() {
        BoardTicket assigned = ticket("eat_a", BoardCase.EAT_TO_FULL, Optional.of(SPIRIT_A));

        assertFalse(
                EvergreenRules.issuedTo(assigned, SPIRIT_B, HUNGRY, FREE),
                "Адресное задание чужому духу не выдаётся: владелец назван, подменять его нельзя"
                        + " Иначе тест issuedTo_AssignedOtherSpirit_NotIssued перестанет ловить эту поломку. При смене issuedTo_AssignedOtherSpirit_NotIssued проверь это утверждение.");
    }

    @Test
    void issuedTo_UnassignedSpirit_Available() {
        BoardTicket open = ticket("eat_any", BoardCase.EAT_TO_FULL, Optional.empty());

        assertTrue(
                EvergreenRules.issuedTo(open, SPIRIT_B, HUNGRY, FREE),
                "Задание без владельца доступно любому применимому духу: другой свободный дух "
                        + "берёт его, когда назначенный не применим"
                        + " Иначе тест issuedTo_UnassignedSpirit_Available перестанет ловить эту поломку. При смене issuedTo_UnassignedSpirit_Available проверь это утверждение.");
    }

    @Test
    void issuedTo_AssignedFullSpirit_NotIssued() {
        BoardTicket assigned = ticket("eat_a", BoardCase.EAT_TO_FULL, Optional.of(SPIRIT_A));

        assertFalse(
                EvergreenRules.issuedTo(assigned, SPIRIT_A, FULL, FREE),
                "Сытого владельца задание не получает: задание остаётся на доске, но этот дух "
                        + "его не берёт — ждёт применимого или снятия адреса"
                        + " Иначе тест issuedTo_AssignedFullSpirit_NotIssued перестанет ловить эту поломку. При смене issuedTo_AssignedFullSpirit_NotIssued проверь это утверждение.");
    }

    @Test
    void issuedTo_NullArgs_AreRejected() {
        BoardTicket assigned = ticket("eat_a", BoardCase.EAT_TO_FULL, Optional.of(SPIRIT_A));

        assertThrows(
                NullPointerException.class,
                () -> EvergreenRules.issuedTo(null, SPIRIT_A, HUNGRY, FREE),
                "Задание null обязано отвергаться: выдавать нечего"
                        + " Иначе тест issuedTo_NullArgs_AreRejected перестанет ловить эту поломку. При смене issuedTo_NullArgs_AreRejected проверь это утверждение.");
        assertThrows(
                NullPointerException.class,
                () -> EvergreenRules.issuedTo(assigned, null, HUNGRY, FREE),
                "Дух null обязан отвергаться: выдача идёт конкретному духу"
                        + " Иначе тест issuedTo_NullArgs_AreRejected перестанет ловить эту поломку. При смене issuedTo_NullArgs_AreRejected проверь это утверждение.");
    }

    private static BoardTicket ticket(String id, BoardCase boardCase, Optional<UUID> assignee) {
        return new BoardTicket(
                id,
                boardCase,
                assignee,
                BoardRules.priorityOf(boardCase),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
    }
}
