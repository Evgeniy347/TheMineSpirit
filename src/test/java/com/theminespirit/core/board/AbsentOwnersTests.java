package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AbsentOwnersTests {
    private static final UUID REMOVED = new UUID(0, 0x7b85ab0cL);
    private static final UUID ALIVE = new UUID(0, 1);

    private static BoardTicket stock(UUID owner) {
        return new BoardTicket(
                "stock:craft_wooden_pickaxe:" + owner,
                BoardCase.CRAFT_WOODEN_PICKAXE,
                Optional.of(owner),
                BoardRules.priorityOf(BoardCase.CRAFT_WOODEN_PICKAXE),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
    }

    private static BoardTicket transferTo(UUID to) {
        return new BoardTicket(
                "transfer:" + to,
                BoardCase.TRANSFER_ITEM,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.TRANSFER_ITEM),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of(),
                TransferTicket.modifiers(to, "minecraft:stone_pickaxe", 1, null));
    }

    @Test
    void sweep_StockOfRemovedSpiritAfterGrace_Cancelled() {
        Board board = new Board(() -> 0);
        board.submit(stock(REMOVED));
        board.submit(transferTo(REMOVED));
        AbsentOwners owners = new AbsentOwners();

        List<String> early = owners.sweep(board, Set.of(ALIVE), 0);
        List<String> late = owners.sweep(board, Set.of(ALIVE), AbsentOwners.GRACE_TICKS);

        assertTrue(early.isEmpty(), "До срока задания не снимаются: дух мог не успеть загрузиться, снято " + early);
        assertEquals(
                Set.of("stock:craft_wooden_pickaxe:" + REMOVED, "transfer:" + REMOVED),
                Set.copyOf(late),
                "Запас и передача удалённому духу снимаются (INC-94: stock:craft_wooden_pickaxe висел за удалённым "
                        + "7b85ab0c), иначе висят на доске вечно");
    }

    @Test
    void sweep_OwnerReturnedBeforeGrace_Kept() {
        Board board = new Board(() -> 0);
        board.submit(stock(REMOVED));
        AbsentOwners owners = new AbsentOwners();

        owners.sweep(board, Set.of(ALIVE), 0);
        owners.sweep(board, Set.of(ALIVE, REMOVED), 10);
        List<String> later = owners.sweep(board, Set.of(ALIVE), AbsentOwners.GRACE_TICKS);

        assertTrue(
                later.isEmpty(),
                "Вернувшийся дух сбрасывает отсчёт: его запас не снимается за прошлое отсутствие, снято " + later);
    }
}
