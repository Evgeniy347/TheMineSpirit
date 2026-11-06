package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StageAuditDecideTests {
    private static final String LIVE_BOARD = "/live/minereopen14/board.json";
    private static final int FENCE_DONE = 70;
    private static final int FENCE_TOTAL = 70;
    private static final int MINE_DONE = 1524 - 59;
    private static final int MINE_TOTAL = 1524;

    @Test
    void decide_LiveFailedFenceButWorldComplete_ClosesWithoutNewTicket() throws IOException {
        Board board = LiveBoardFixture.load(LIVE_BOARD);
        BoardTicket fence = board.find("first:build_fence")
                .orElseThrow(() -> new AssertionError("В живой доске observe14 нет first:build_fence"));
        assertEquals(
                BoardStatus.FAILED,
                board.statusOf("first:build_fence").orElseThrow(),
                "Живая ограда observe14 держится в FAILED при готовом мире: " + fence);
        assertEquals(
                Map.of(),
                fence.requiredItems(),
                "Материалы ограды уже пусты, а задача всё равно перевыдаётся: " + fence);

        assertEquals(
                StageAudit.Decision.COMPLETE,
                StageAudit.decide(BoardStatus.FAILED, false, FENCE_DONE, FENCE_TOTAL),
                "Ограда 70/70 стоит в мире, хотя тикет FAILED: готовая постройка закрывается по факту мира, "
                        + "а не по статусу тикета");
    }

    @Test
    void decide_LiveDoneMineButDamaged_NeedsAuditRepair() throws IOException {
        Board board = LiveBoardFixture.load(LIVE_BOARD);
        BoardTicket mine = board.find("first:build_mine")
                .orElseThrow(() -> new AssertionError("В живой доске observe14 нет first:build_mine"));
        assertEquals(
                BoardStatus.DONE,
                board.statusOf("first:build_mine").orElseThrow(),
                "Живая шахта observe14 закрыта доской: " + mine);
        assertEquals(
                59,
                mine.requiredItems().get("cobblestone"),
                "Живой тикет шахты несёт 59 недостающих полов — по ним и нужен ремонт: " + mine);

        assertEquals(
                StageAudit.Decision.AUDIT,
                StageAudit.decide(BoardStatus.DONE, true, MINE_DONE, MINE_TOTAL),
                "Шахта DONE при 96% повреждена: аудит обязан заказать ремонт по недостающим блокам, "
                        + "а не считать постройку целой");
    }

    @Test
    void decide_UnstartedBuild_OpensPlan() {
        assertEquals(
                StageAudit.Decision.OPEN,
                StageAudit.decide(null, true, 0, FENCE_TOTAL),
                "Шаг без тикета и без блоков в мире открывается обычным планом");
        assertEquals(
                StageAudit.Decision.AUDIT,
                StageAudit.decide(BoardStatus.FAILED, false, 20, FENCE_TOTAL),
                "Частично стоящая постройка чинится по недостающим блокам, а не перевыдаётся полным планом: "
                        + "иначе ограда 70/71 висит без задач");
    }
}
