package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BoardLedgerTests {

    @Test
    void key_BuildingLevel_HasPrefixFormat() {
        assertEquals(
                "building:BUILD_MINE:1",
                BoardLedger.key(BoardCase.BUILD_MINE, 1),
                "Ключ отметки — building:<case>:<level>: по нему условие «уровень 1 шахты готов» "
                        + "находят и доска, и журнал прошлой сессии"
                        + " Иначе тест key_BuildingLevel_HasPrefixFormat перестанет ловить эту поломку. При смене key_BuildingLevel_HasPrefixFormat проверь это утверждение.");
    }

    @Test
    void note_ThenHas_ReturnsTrue() {
        BoardLedger ledger = new BoardLedger();
        ledger.note(BoardCase.BUILD_MINE, 1);

        assertTrue(
                ledger.has(BoardCase.BUILD_MINE, 1),
                "Отмеченный уровень виден: зависимое задание выдаётся после выполнения уровня"
                        + " Иначе тест note_ThenHas_ReturnsTrue перестанет ловить эту поломку. При смене note_ThenHas_ReturnsTrue проверь это утверждение.");
        assertFalse(
                ledger.has(BoardCase.BUILD_MINE, 2),
                "Неотмеченный уровень не виден: уровень не прыгает через неотмеченный"
                        + " Иначе тест note_ThenHas_ReturnsTrue перестанет ловить эту поломку. При смене note_ThenHas_ReturnsTrue проверь это утверждение.");
        assertFalse(
                ledger.has(BoardCase.BUILD_PEN, 1),
                "Чужой кейс не виден: отметка шахты не открывает загон"
                        + " Иначе тест note_ThenHas_ReturnsTrue перестанет ловить эту поломку. При смене note_ThenHas_ReturnsTrue проверь это утверждение.");
    }

    @Test
    void key_ZeroLevel_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BoardLedger.key(BoardCase.BUILD_MINE, 0),
                "Нулевой уровень не отмечается: первой всегда стоит отметка уровня 1"
                        + " Иначе тест key_ZeroLevel_Rejected перестанет ловить эту поломку. При смене key_ZeroLevel_Rejected проверь это утверждение.");
    }

    @Test
    void parse_BuildingKey_ReturnsMark() {
        Optional<BoardLedger.BuildingMark> mark = BoardLedger.parse("building:BUILD_MINE:2");

        assertEquals(
                Optional.of(new BoardLedger.BuildingMark(BoardCase.BUILD_MINE, 2)),
                mark,
                "Зависимость building:BUILD_MINE:2 разбирается в отметку: по ней доска ищет "
                        + "живое DONE-задание или запись ledger"
                        + " Иначе тест parse_BuildingKey_ReturnsMark перестанет ловить эту поломку. При смене parse_BuildingKey_ReturnsMark проверь это утверждение.");
    }

    @Test
    void parse_TicketId_ReturnsEmpty() {
        assertEquals(
                Optional.empty(),
                BoardLedger.parse("capture_1"),
                "Обычный id задания — не отметка постройки: id-зависимости проверяются "
                        + "по статусу DONE, а не по ledger"
                        + " Иначе тест parse_TicketId_ReturnsEmpty перестанет ловить эту поломку. При смене parse_TicketId_ReturnsEmpty проверь это утверждение.");
    }

    @Test
    void parse_BrokenKeys_ReturnEmpty() {
        assertEquals(
                Optional.empty(),
                BoardLedger.parse("building:BUILD_MINE"),
                "Ключ без уровня — не отметка: такое условие никогда не выполнено, " + "а не падает на пересборке"
                        + " Иначе тест parse_BrokenKeys_ReturnEmpty перестанет ловить эту поломку. При смене parse_BrokenKeys_ReturnEmpty проверь это утверждение.");
        assertEquals(
                Optional.empty(),
                BoardLedger.parse("building:ПОЧИНИТЬ_ВСЁ:1"),
                "Неизвестный кейс — не отметка: опечатка в условии держит задание "
                        + "вне выдачи, а не роняет пересборку"
                        + " Иначе тест parse_BrokenKeys_ReturnEmpty перестанет ловить эту поломку. При смене parse_BrokenKeys_ReturnEmpty проверь это утверждение.");
        assertEquals(
                Optional.empty(),
                BoardLedger.parse("building:BUILD_MINE:0"),
                "Нулевой уровень — не отметка: уровней ниже первого не существует"
                        + " Иначе тест parse_BrokenKeys_ReturnEmpty перестанет ловить эту поломку. При смене parse_BrokenKeys_ReturnEmpty проверь это утверждение.");
        assertEquals(
                Optional.empty(),
                BoardLedger.parse(null),
                "Null — не отметка: зависимости без условия проверяют по id"
                        + " Иначе тест parse_BrokenKeys_ReturnEmpty перестанет ловить эту поломку. При смене parse_BrokenKeys_ReturnEmpty проверь это утверждение.");
    }

    @Test
    void stageGate_SecondCall_ReturnsCached() {
        BoardLedger ledger = new BoardLedger();

        StageGate first = ledger.stageGate();

        assertSame(
                first,
                ledger.stageGate(),
                "Гейт этапа пересчитывается один раз: проверка защиты блока не должна заново строить"
                        + " и сортировать весь остаток этапа на каждом блоке"
                        + " Иначе тест stageGate_SecondCall_ReturnsCached перестанет ловить эту поломку. При смене stageGate_SecondCall_ReturnsCached проверь это утверждение.");
    }

    @Test
    void note_AfterGateBuilt_InvalidatesCache() {
        BoardLedger ledger = new BoardLedger();
        StageGate first = ledger.stageGate();
        ledger.note(BoardCase.BUILD_MINE, 1);
        BoardLedger fresh = new BoardLedger();
        fresh.note(BoardCase.BUILD_MINE, 1);

        assertNotSame(
                first,
                ledger.stageGate(),
                "Новая отметка сбрасывает кеш гейта: иначе доска видит старый остаток этапа и задания не идут"
                        + " Иначе тест note_AfterGateBuilt_InvalidatesCache перестанет ловить эту поломку. При смене note_AfterGateBuilt_InvalidatesCache проверь это утверждение.");
        assertEquals(
                fresh.stageGate().missing(),
                ledger.stageGate().missing(),
                "После сброса кеша гейт совпадает со свежим журналом с той же отметкой: кеш не меняет смысл этапа"
                        + " Иначе тест note_AfterGateBuilt_InvalidatesCache перестанет ловить эту поломку. При смене note_AfterGateBuilt_InvalidatesCache проверь это утверждение.");
    }
}
