package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardStuckFactTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static Board board() {
        return new Board(() -> 0);
    }

    private static BoardTicket repair() {
        return new BoardTicket(
                "world:plan_breach:0,1,0:9",
                BoardCase.REPAIR_BUILDING,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.REPAIR_BUILDING),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
    }

    private static Board claimed() {
        Board board = board();
        BoardTicket ticket = repair();
        board.submit(ticket);
        board.claim(ticket.id(), SPIRIT, 0);
        board.stuck().see(SPIRIT, new BlockCoord(0, 1, 0), 0);
        return board;
    }

    @Test
    void fail_NoPathFactFromMovement_MarksSpiritEvenWhenNeedIsItem() {
        Board board = claimed();

        board.fail(
                repair().id(),
                new StockView(Map.of()),
                "к клетке (0, 1, 0) не подойти: ни одной стоянки",
                new GraphBlock.Item("dirt", 1),
                true);

        assertTrue(
                board.stuck().isStuck(SPIRIT, 0),
                "Провал движения ставит метку духу, даже если задание ждало материал: spirit-6 стоял на площадке "
                        + "час, потому что «пути нет» приходило с блокировкой Item, и метка не ставилась");
        assertTrue(
                board.stuck().of(SPIRIT, 0).orElseThrow().noPath(),
                "Метка несёт факт «пути нет»: по нему граф ставит rescue, а не повторяет ту же работу");
    }

    @Test
    void fail_ItemNeedWithoutMovementFact_DoesNotMarkSpirit() {
        Board board = claimed();

        board.fail(repair().id(), new StockView(Map.of()), "нет материала 'dirt'", new GraphBlock.Item("dirt", 1));

        assertFalse(
                board.stuck().isStuck(SPIRIT, 0),
                "Нехватка материала — не «пути нет»: иначе дух с меткой не получит задачу добыть материал "
                        + "и будет ждать rescue там, где он не нужен");
    }
}
