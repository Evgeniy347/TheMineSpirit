package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BoardWhyTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final BoardCase CASE = BoardCase.STOCK_CHESTS;
    private static final StockView EMPTY = new StockView(Map.of());

    private static BoardTicket ticket(String id, Optional<UUID> assignee, Map<String, Integer> required) {
        return new BoardTicket(
                id, CASE, assignee, BoardRules.priorityOf(CASE), 0, 1, BoardTicket.ALWAYS_CAUSE, List.of(), required);
    }

    @Test
    void waitReason_HolderPausedAfterFailure_NamesPause() {
        long[] now = {0};
        Board board = new Board(() -> now[0]);
        board.submit(ticket("failed", Optional.empty(), Map.of()));
        board.claim("failed", SPIRIT, 0);
        board.fail("failed");
        BoardTicket own = ticket("own", Optional.of(SPIRIT), Map.of());
        board.submit(own);

        Optional<String> reason = BoardWhy.waitReason(board, own, EMPTY, 0);

        assertTrue(
                reason.filter(text -> text.contains("пауз")).isPresent(),
                "PENDING-задание держателя на паузе кейса объясняет простой (INC-23: 15 минут догадок), "
                        + "фактически " + reason);
    }

    @Test
    void waitReason_MissingMaterials_NamesShortage() {
        Board board = new Board(() -> 0);
        BoardTicket gated = ticket("gated", Optional.empty(), Map.of("cobblestone", 8));
        board.submit(gated);

        assertEquals(
                Optional.of("не хватает материалов: {cobblestone=8}"),
                BoardWhy.waitReason(board, gated, EMPTY, 0),
                "Гейт материалов называет нехватку");
    }

    @Test
    void waitReason_ReadyTicket_Empty() {
        Board board = new Board(() -> 0);
        BoardTicket ready = ticket("ready", Optional.empty(), Map.of());
        board.submit(ready);

        assertEquals(
                Optional.empty(),
                BoardWhy.waitReason(board, ready, EMPTY, 0),
                "Готовое задание ждёт только свободного духа — причины ядра нет");
    }
}
