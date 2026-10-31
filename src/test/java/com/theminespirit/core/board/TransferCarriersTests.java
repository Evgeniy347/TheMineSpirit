package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferCarriersTests {
    private static final UUID SELF = new UUID(0, 1);
    private static final String CRAFT = "stock:craft_stone_axe:spirit-1";
    private static final String TRANSFER = "transfer:" + CRAFT + ":cobblestone:warehouse";
    private static final EatNeed FED = new EatNeed(20, 20, 20, 20);

    private static Board boardWithTransfer() {
        Board board = new Board(() -> 0);
        board.submit(new BoardTicket(
                TRANSFER,
                BoardCase.TRANSFER_ITEM,
                Optional.of(SELF),
                BoardRules.priorityOf(BoardCase.TRANSFER_ITEM),
                0,
                1,
                TransferCarriers.cause(CRAFT),
                List.of(),
                Map.of(),
                TransferTicket.modifiers(SELF, "cobblestone", 3, null)));
        board.submit(new BoardTicket(
                "first:build_house",
                BoardCase.BUILD_HOUSE,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.BUILD_HOUSE),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of()));
        return board;
    }

    @Test
    void dropUnplanned_TransferWithoutPlan_HolderFreeInSameRound() {
        Board board = boardWithTransfer();
        DispatchRound round = new DispatchRound(board, 0);
        TransferCarriers.carrying(board, CRAFT, Set.of()).forEach(donor -> round.await(donor, "несёт"));
        BoardTicket transfer = board.all().stream()
                .filter(ticket -> TRANSFER.equals(ticket.id()))
                .findFirst()
                .orElseThrow();

        TransferCarriers.dropUnplanned(board, round, transfer);

        DispatchRound.Candidate self =
                new DispatchRound.Candidate(SELF, "spirit-1", null, List.of(), FED, SpiritView.free());
        BoardTicket house = board.all().stream()
                .filter(ticket -> ticket.boardCase() == BoardCase.BUILD_HOUSE)
                .findFirst()
                .orElseThrow();
        assertEquals(
                List.of(Optional.empty(), Set.of()),
                List.of(round.refusal(house, self), TransferCarriers.carrying(board, CRAFT, Set.of())),
                "INC-86: передача, план которой не собрался, снимается, и держатель в ту же пересборку свободен для "
                        + "других заданий; иначе он вечно «несёт ингредиенты» и стопорит всю доску");
    }

    private static Board boardWithBuild() {
        Board board = new Board(() -> 0);
        board.submit(new BoardTicket(
                "first:build_fence",
                BoardCase.BUILD_FENCE,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.BUILD_FENCE),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of()));
        return board;
    }

    @Test
    void awaitsDelivery_NoTransfer_FenceNotHeldByBusyHolder() {
        Board board = boardWithBuild();

        assertFalse(
                TransferCarriers.awaitsDelivery(board, "first:build_fence"),
                "Занятый дух с палками в кармане — не носильщик: передачу ему не выдать, а держателем его считать "
                        + "значит держать ограду вечно в «носильщиков нет» (observe8 №3)");
        assertTrue(
                board.claimable(new StockView(Map.of()), 0).stream()
                        .anyMatch(ticket -> "first:build_fence".equals(ticket.id())),
                "Без свободных носильщиков задание всё равно продвигается: исполнитель берёт ограду в работу и "
                        + "забирает ингредиенты со склада или крафтит палки сам, а не ждёт вечно");
    }

    @Test
    void awaitsDelivery_OpenTransfer_TrueUntilClosed() {
        Board board = boardWithTransfer();

        assertTrue(
                TransferCarriers.awaitsDelivery(board, CRAFT),
                "Пока передача открыта, исполнитель ждёт доставку: иначе начнёт работу без ингредиентов");

        board.cancel(TRANSFER);

        assertFalse(
                TransferCarriers.awaitsDelivery(board, CRAFT),
                "Закрытая передача не держит исполнителя: иначе ожидание не кончается никогда");
    }

    @Test
    void carrying_ClaimedTransferWithRunningTask_HolderCarries() {
        Board board = boardWithTransfer();
        board.claim(TRANSFER, SELF, 0);

        assertEquals(
                Set.of(SELF),
                TransferCarriers.carrying(board, CRAFT, Set.of(SELF)),
                "Взятая передача с запущенной задачей — дух несёт груз и занят до сдачи");
        assertEquals(
                Set.of(),
                TransferCarriers.carrying(board, CRAFT, Set.of()),
                "Взятая передача без запущенной задачи — дух ничего не несёт и не числится занятым");
    }
}
