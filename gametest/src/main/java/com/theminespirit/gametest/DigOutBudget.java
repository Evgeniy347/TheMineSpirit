package com.theminespirit.gametest;

import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.movement.SurfaceRoute;
import net.minecraft.gametest.framework.GameTestHelper;

final class DigOutBudget {
    private static final long BOARD_MILLIS = 500;
    private static final long SLICE_SLACK_MILLIS = 50;

    private final BoardService board;
    private long last;
    private long max;
    private boolean warmed;

    private DigOutBudget(BoardService board) {
        this.board = board;
        this.last = board.meter().rebuildMillis();
    }

    static DigOutBudget start(BoardService board) {
        board.meter().reset();
        SurfaceRoute.resetSliceMeter();
        return new DigOutBudget(board);
    }

    void sample() {
        long millis = board.meter().rebuildMillis();
        if (millis == last) {
            return;
        }
        last = millis;
        if (!warmed) {
            warmed = true;
            return;
        }
        max = Math.max(max, millis);
    }

    long maxMillis() {
        return max;
    }

    static void assertWithin(GameTestHelper helper, long boardMillis) {
        helper.assertTrue(
                boardMillis <= BOARD_MILLIS
                        && SurfaceRoute.maxSliceMillis() <= SurfaceRoute.SLICE_MILLIS + SLICE_SLACK_MILLIS,
                "Пересборка доски " + boardMillis + " мс (бюджет " + BOARD_MILLIS + ") и поиск пути "
                        + SurfaceRoute.maxSliceMillis() + " мс (бюджет " + SurfaceRoute.SLICE_MILLIS
                        + ") обязаны укладываться в тик: сервер 0.1.3595 стоял с board_ms 1514 и тиком 3,4 с");
    }
}
