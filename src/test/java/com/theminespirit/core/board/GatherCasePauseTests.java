package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GatherCasePauseTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static BoardTicket gather(String id) {
        return new BoardTicket(
                id,
                BoardCase.GATHER_MATERIALS,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.GATHER_MATERIALS),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
    }

    private static void failOnce(Board board, String id, UUID spirit, long now) {
        board.submit(gather(id));
        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim(id, spirit, now),
                "Задание " + id + " выдано духу: иначе провал не к чему привязать");
        board.fail(id);
    }

    @Test
    void pause_OtherItemFailed_DoesNotStarveIdleSpirit() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        failOnce(board, "graph:make:oak_planks", SPIRIT, now[0]);

        board.submit(gather("supply_gather:cobblestone"));

        assertEquals(
                BoardOutcome.CLAIMED,
                board.claim("supply_gather:cobblestone", SPIRIT, now[0]),
                "Простаивающий дух обязан взять добычу другого предмета: пауза кейса после провалов досок не "
                        + "должна голодить все 11 PENDING GATHER_MATERIALS");
    }

    @Test
    void pause_SameItemFailed_StillHoldsSpirit() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        failOnce(board, "graph:make:oak_planks", SPIRIT, now[0]);

        board.submit(gather("graph:make:oak_planks:2"));

        assertEquals(
                BoardOutcome.SPIRIT_PAUSED,
                board.claim("graph:make:oak_planks:2", SPIRIT, now[0]),
                "Тот же предмет после провала не выдаётся за тик до конца паузы: иначе дух долбит "
                        + "недостижимый источник без отката");
    }
}
