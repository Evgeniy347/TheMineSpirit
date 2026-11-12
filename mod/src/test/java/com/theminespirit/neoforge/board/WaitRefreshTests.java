package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class WaitRefreshTests {
    @Test
    void window_ManyTickets_TakesBatchAndWraps() {
        assertArrayEquals(
                new int[] {598, 599, 0, 1},
                WaitRefresh.window(598, 600, 4),
                "причина ожидания пересчитывается порцией по кругу, а не для всех ~600 заявок за такт (сервер 12.11:"
                        + " reconcile держал тик 810 мс)");
    }

    @Test
    void window_FewTickets_TakesAllOnce() {
        assertArrayEquals(new int[] {1, 2, 0}, WaitRefresh.window(1, 3, 40), "заявок меньше порции — каждая один раз");
    }
}
