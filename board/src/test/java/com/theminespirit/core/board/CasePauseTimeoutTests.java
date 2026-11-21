package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CasePauseTimeoutTests {
    private static final UUID SPIRIT = UUID.fromString("33333333-4444-5555-6666-777777777777");
    private static final BoardCase CASE = BoardCase.GATHER_MATERIALS;

    private static BoardTicket gather(String id) {
        return new BoardTicket(
                id,
                CASE,
                Optional.empty(),
                BoardRules.priorityOf(CASE),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
    }

    private static long detach(CasePauseBook book, BoardTicket ticket) {
        long now = 0;
        for (int attempt = 1; attempt <= BoardTuning.caseFailLimit(); attempt++) {
            book.noteFailure(SPIRIT, ticket, now);
            if (attempt < BoardTuning.caseFailLimit()) {
                now += BoardBackoff.pauseTicks(attempt);
            }
        }
        assertTrue(
                book.detached(SPIRIT, CASE),
                "До предела провалов снятия ещё нет"
                        + " Иначе тест механику перестанет ловить эту поломку. При смене механику проверь это утверждение.");
        return now;
    }

    @Test
    void detached_SameItemRepeated_FreesAfterTimeout() {
        CasePauseBook book = new CasePauseBook();
        BoardTicket ticket = gather("graph:make:oak_planks");
        long detachedAt = detach(book, ticket);
        long release = detachedAt + BoardTuning.caseFailPauseCapTicks();

        assertFalse(
                book.available(SPIRIT, CASE, release - 1),
                "За тик до таймаута снятый кейс не возвращается духу: иначе снятие не откатывает цикл"
                        + " При смене detached_SameItemRepeated_FreesAfterTimeout проверь это утверждение.");
        assertTrue(
                book.available(SPIRIT, CASE, release),
                "По таймауту снятие кончается: иначе один провал навсегда паркует духа без задачи "
                        + "(живой прогон: activity=SMELT, script=null, 40 минут простоя)"
                        + " При смене detached_SameItemRepeated_FreesAfterTimeout проверь это утверждение.");
        assertFalse(
                book.detached(SPIRIT, CASE),
                "После таймаута дух снова не считается снятым с кейса"
                        + " Иначе тест detached_SameItemRepeated_FreesAfterTimeout перестанет ловить эту поломку. При смене detached_SameItemRepeated_FreesAfterTimeout проверь это утверждение.");
    }

    @Test
    void detached_AllSpiritsIdle_ReleasesWithoutWaitingForTimeout() {
        CasePauseBook book = new CasePauseBook();
        BoardTicket ticket = gather("graph:make:oak_planks");
        long detachedAt = detach(book, ticket);

        assertTrue(
                book.releaseDetached() == 1,
                "Запасное действие при простое всех духов снимает ровно одно снятие с кейса: иначе город "
                        + "стоит до таймаута"
                        + " При смене detached_AllSpiritsIdle_ReleasesWithoutWaitingForTimeout проверь это утверждение.");
        assertTrue(
                book.available(SPIRIT, ticket, detachedAt),
                "После запасного действия дух снова берёт кейс, не дожидаясь таймаута"
                        + " Иначе тест detached_AllSpiritsIdle_ReleasesWithoutWaitingForTimeout перестанет ловить эту поломку. При смене detached_AllSpiritsIdle_ReleasesWithoutWaitingForTimeout проверь это утверждение.");
    }

    @Test
    void detached_OtherItem_TriesImmediately() {
        CasePauseBook book = new CasePauseBook();
        BoardTicket planks = gather("graph:make:oak_planks");
        long detachedAt = detach(book, planks);

        assertFalse(
                book.available(SPIRIT, planks, detachedAt),
                "Тот же предмет после снятия не выдаётся сразу: пауза держит до таймаута"
                        + " Иначе тест detached_OtherItem_TriesImmediately перестанет ловить эту поломку. При смене detached_OtherItem_TriesImmediately проверь это утверждение.");
        assertTrue(
                book.available(SPIRIT, gather("graph:make:oak_log"), detachedAt),
                "Смена условий (другой предмет добычи) снимает снятие сразу: дух не ждёт таймаут "
                        + "на недостижимом предмете, когда рядом есть другой"
                        + " Иначе тест detached_OtherItem_TriesImmediately перестанет ловить эту поломку. При смене detached_OtherItem_TriesImmediately проверь это утверждение.");
        assertFalse(
                book.detached(SPIRIT, CASE),
                "Новый предмет сбрасывает и отметку снятия кейса"
                        + " Иначе тест detached_OtherItem_TriesImmediately перестанет ловить эту поломку. При смене detached_OtherItem_TriesImmediately проверь это утверждение.");
    }
}
