package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BoardTicketPart4Tests {
    private static BoardTicket ticket(String title) {
        return new BoardTicket(
                        "rest:t-1",
                        BoardCase.ACTIVITY_EXTERNAL,
                        Optional.empty(),
                        95,
                        0,
                        1,
                        "always",
                        List.of(),
                        Map.of())
                .withTitle(title);
    }

    @Test
    void withTitle_KeepsRestOfTicket_WinsInSameDetails() {
        BoardTicket named = ticket("Поход за деревом");

        assertEquals(
                "Поход за деревом",
                named.title(),
                "Название обязано сохраняться на билете: иначе плитка доски не отличит задачу стратега от "
                        + "случайного занятия. При смене withTitle в BoardTicket проверь сохранение полей");
        assertEquals(
                BoardCase.ACTIVITY_EXTERNAL,
                named.boardCase(),
                "Название не подменяет кейс: по кейсу доска решает выдачу и показ, иначе REST-тикет "
                        + "перестаёт считаться занятием. При смене withTitle в BoardTicket проверь кейс");
        assertFalse(
                named.sameDetails(ticket(null)),
                "Смена названия обязана менять детали билета: иначе пересборка посчитает задачу прежней и "
                        + "оставит на доске старое имя. При смене sameDetails в BoardTicket проверь title");
        assertTrue(
                ticket("То же").sameDetails(ticket("То же")),
                "Одинаковое название не делает билеты разными: иначе каждая пересборка снимает и ставит "
                        + "тикет заново. При смене sameDetails в BoardTicket проверь равенство title");
    }

    @Test
    void withTitle_BlankText_BecomesNull() {
        assertNull(
                ticket("   ").title(),
                "Пробельное название обязано стать null, иначе плитка рисует пустую строку вместо имени "
                        + "кейса и пользователь теряет объяснение работы. При смене нормализации title в "
                        + "BoardTicket проверь пробельное значение");
    }
}
