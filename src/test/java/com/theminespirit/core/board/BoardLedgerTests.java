package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class BoardLedgerTests {

    @Test
    void key_BuildingLevel_HasPrefixFormat() {
        assertEquals("building:BUILD_MINE:1", BoardLedger.key(BoardCase.BUILD_MINE, 1),
                "Ключ отметки — building:<case>:<level>: по нему условие «уровень 1 шахты готов» "
                        + "находят и доска, и журнал прошлой сессии");
    }

    @Test
    void note_ThenHas_ReturnsTrue() {
        BoardLedger ledger = new BoardLedger();
        ledger.note(BoardCase.BUILD_MINE, 1);

        assertTrue(ledger.has(BoardCase.BUILD_MINE, 1),
                "Отмеченный уровень виден: зависимое задание выдаётся после выполнения уровня");
        assertFalse(ledger.has(BoardCase.BUILD_MINE, 2),
                "Неотмеченный уровень не виден: уровень не прыгает через неотмеченный");
        assertFalse(ledger.has(BoardCase.BUILD_PEN, 1),
                "Чужой кейс не виден: отметка шахты не открывает загон");
    }

    @Test
    void key_ZeroLevel_Rejected() {
        assertThrows(IllegalArgumentException.class, () -> BoardLedger.key(BoardCase.BUILD_MINE, 0),
                "Нулевой уровень не отмечается: первой всегда стоит отметка уровня 1");
    }

    @Test
    void parse_BuildingKey_ReturnsMark() {
        Optional<BoardLedger.BuildingMark> mark = BoardLedger.parse("building:BUILD_MINE:2");

        assertEquals(Optional.of(new BoardLedger.BuildingMark(BoardCase.BUILD_MINE, 2)), mark,
                "Зависимость building:BUILD_MINE:2 разбирается в отметку: по ней доска ищет "
                        + "живое DONE-задание или запись ledger");
    }

    @Test
    void parse_TicketId_ReturnsEmpty() {
        assertEquals(Optional.empty(), BoardLedger.parse("capture_1"),
                "Обычный id задания — не отметка постройки: id-зависимости проверяются "
                        + "по статусу DONE, а не по ledger");
    }

    @Test
    void parse_BrokenKeys_ReturnEmpty() {
        assertEquals(Optional.empty(), BoardLedger.parse("building:BUILD_MINE"),
                "Ключ без уровня — не отметка: такое условие никогда не выполнено, "
                        + "а не падает на пересборке");
        assertEquals(Optional.empty(), BoardLedger.parse("building:ПОЧИНИТЬ_ВСЁ:1"),
                "Неизвестный кейс — не отметка: опечатка в условии держит задание "
                        + "вне выдачи, а не роняет пересборку");
        assertEquals(Optional.empty(), BoardLedger.parse("building:BUILD_MINE:0"),
                "Нулевой уровень — не отметка: уровней ниже первого не существует");
        assertEquals(Optional.empty(), BoardLedger.parse(null),
                "Null — не отметка: зависимости без условия проверяют по id");
    }
}
