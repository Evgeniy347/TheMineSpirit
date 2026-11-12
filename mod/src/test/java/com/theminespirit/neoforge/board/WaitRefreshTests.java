package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WaitRefreshTests {
    @Test
    void window_ManyTickets_TakesBatchAndWraps() {
        assertArrayEquals(
                new int[] {598, 599, 0, 1},
                WaitRefresh.window(598, 600, 4),
                "причина ожидания пересчитывается порцией по кругу, а не для всех ~600 заявок за такт (сервер 12.11:"
                        + " reconcile держал тик 810 мс). Сломается: без переноса через конец хвост списка никогда не"
                        + " обновит waitReason в /v1/board. Проверить при смене: порядок DispatchRound.order и курсор");
    }

    @Test
    void window_FewTickets_TakesAllOnce() {
        assertArrayEquals(
                new int[] {1, 2, 0},
                WaitRefresh.window(1, 3, 40),
                "заявок меньше порции — каждая ровно один раз: повтор одной заявки в порции удвоит стоимость"
                        + " выбора духа, пропуск оставит устаревшую причину ожидания. Проверить при смене: границы окна");
    }

    @Test
    void window_FullBoard_CostIsOneBatch() {
        assertEquals(
                WaitRefresh.BATCH,
                WaitRefresh.window(0, 600, WaitRefresh.BATCH).length,
                "раздача и сверка объясняют ожидание не больше порции за проход (сервер 12.11: pick.why() для ~600"
                        + " заявок держал раздачу ≈400 мс). Сломается: тик снова встанет на секунды при большой доске."
                        + " Проверить при смене: BoardDispatchDesk.issueAll (счётчик explained) и WaitRefresh.tick");
    }
}
