package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StageRetryTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String ID = "first:build_orchard";

    private static BoardTicket ticket(long createdAt) {
        return new BoardTicket(
                ID,
                BoardCase.BUILD_ORCHARD,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.BUILD_ORCHARD),
                createdAt,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
    }

    @Test
    void prune_FailedFirstStep_KeptForRetry() {
        Board board = new Board(() -> 0L);
        board.submit(ticket(0));
        board.claim(ID, SPIRIT, 0);
        board.fail(ID);

        board.prune(0);

        assertEquals(
                Optional.of(BoardStatus.FAILED),
                board.statusOf(ID),
                "Проваленный шаг первого этапа остаётся на доске: иначе сад с 0 из 24 исчезает с доски (404) "
                        + "и этап 1 не закрыть");
    }

    @Test
    void submit_FailedFirstStep_RepostedAfterBackoff() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        board.submit(ticket(now[0]));
        board.claim(ID, SPIRIT, now[0]);
        board.fail(ID);
        now[0] += BoardTuning.failBackoffTicks();

        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(ticket(now[0])),
                "Проваленный шаг первого этапа доска переиздаёт после отката: задание не пропадает, "
                        + "а возвращается в очередь");
        assertEquals(
                Optional.of(BoardStatus.PENDING),
                board.statusOf(ID),
                "Переизданный шаг снова ждёт выдачи, как и положено шагу, который ещё не построен");
    }
}
