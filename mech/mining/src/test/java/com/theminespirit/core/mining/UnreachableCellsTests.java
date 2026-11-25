package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class UnreachableCellsTests {
    private static final BlockCoord FENCE = new BlockCoord(-182, 66, -524);

    @Test
    void excluded_AfterOneFailure_IsFalse() {
        UnreachableCells cells = new UnreachableCells();

        cells.noteFailure(FENCE);

        assertFalse(
                cells.excluded(FENCE),
                "После первого отказа клетку ещё пробуют: чёрный список не должен срабатывать по случайной помехе"
                        + " Иначе тест excluded_AfterOneFailure_IsFalse перестанет ловить эту поломку. При смене excluded_AfterOneFailure_IsFalse проверь это утверждение.");
    }

    @Test
    void excluded_AfterTwoFailures_IsTrue() {
        UnreachableCells cells = new UnreachableCells();

        cells.noteFailure(FENCE);
        cells.noteFailure(FENCE);

        assertTrue(
                cells.excluded(FENCE),
                "Вкопанный забор без стоянки дважды не дался — клетка уходит в чёрный список, иначе уборка "
                        + "крутится на ней каждую минуту"
                        + " При смене excluded_AfterTwoFailures_IsTrue проверь это утверждение.");
    }

    @Test
    void failureCount_RepeatedCell_CountsEveryAttempt() {
        UnreachableCells cells = new UnreachableCells();

        cells.noteFailure(FENCE);
        cells.noteFailure(FENCE);
        cells.noteFailure(FENCE);

        assertEquals(
                3,
                cells.failureCount(FENCE),
                "Счётчик повторов обязан расти: по нему в логе видно «повтор 3», а не просто «без стоянки»"
                        + " Иначе тест failureCount_RepeatedCell_CountsEveryAttempt перестанет ловить эту поломку. При смене failureCount_RepeatedCell_CountsEveryAttempt проверь это утверждение.");
    }

    @Test
    void reached_AfterFailures_RemovesCellFromBlacklist() {
        UnreachableCells cells = new UnreachableCells();
        cells.noteFailure(FENCE);
        cells.noteFailure(FENCE);

        cells.reached(FENCE);

        assertFalse(
                cells.excluded(FENCE),
                "Если блок всё-таки убрали, он выходит из чёрного списка: условие изменилось, пробовать снова можно"
                        + " Иначе тест reached_AfterFailures_RemovesCellFromBlacklist перестанет ловить эту поломку. При смене reached_AfterFailures_RemovesCellFromBlacklist проверь это утверждение.");
    }

    @Test
    void escalating_AfterTwoAreaAttempts_IsTrue() {
        UnreachableCells cells = new UnreachableCells();
        cells.noteAttempt("-182,66,-524..-182,66,-524");
        cells.noteAttempt("-182,66,-524..-182,66,-524");

        assertTrue(
                cells.escalating("-182,66,-524..-182,66,-524"),
                "Второй провал того же участка обязан поднимать WARN «участок не расчищается»: иначе тупик "
                        + "виден только в конце прогона"
                        + " При смене escalating_AfterTwoAreaAttempts_IsTrue проверь это утверждение.");
    }

    @Test
    void revision_NewFailure_Grows() {
        UnreachableCells cells = new UnreachableCells();
        long before = cells.revision();

        cells.noteFailure(FENCE);

        assertTrue(
                cells.revision() > before,
                "Новый отказ обязан двигать ревизию: иначе проход исключений не узнает о нём и мёртвая клетка останется в работе до конца сессии"
                        + " Иначе тест revision_NewFailure_Grows перестанет ловить эту поломку. При смене revision_NewFailure_Grows проверь это утверждение.");
    }

    @Test
    void revision_ReachedCell_StaysSame() {
        UnreachableCells cells = new UnreachableCells();
        cells.noteFailure(FENCE);
        long afterFailure = cells.revision();

        cells.reached(FENCE);

        assertEquals(
                afterFailure,
                cells.revision(),
                "Достигнутая клетка не двигает ревизию: иначе проход исключений пересканирует сотни тысяч клеток после каждого сломанного блока"
                        + " Иначе тест revision_ReachedCell_StaysSame перестанет ловить эту поломку. При смене revision_ReachedCell_StaysSame проверь это утверждение.");
    }

    @Test
    void escalating_AfterOneAreaAttempt_IsFalse() {
        UnreachableCells cells = new UnreachableCells();
        cells.noteAttempt("-182,66,-524..-182,66,-524");

        assertFalse(
                cells.escalating("-182,66,-524..-182,66,-524"),
                "Первый провал участка — ещё не тупик: WARN положен повтору, а не любой неудаче"
                        + " Иначе тест escalating_AfterOneAreaAttempt_IsFalse перестанет ловить эту поломку. При смене escalating_AfterOneAreaAttempt_IsFalse проверь это утверждение.");
    }
}
