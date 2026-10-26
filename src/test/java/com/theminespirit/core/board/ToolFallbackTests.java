package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ToolFallbackTests {
    private static final ToolsConfig CONFIG = new ToolsConfig(List.of("axe"), "stone", List.of("iron", "diamond"));
    private static final UUID SELF = new UUID(0, 7);
    private static final EatNeed FED = new EatNeed(20, 20, 20, 20);

    @Test
    void missing_NoAxeLogsInPackStoneOnlyViaTransfer_WoodenAxeFirst() {
        List<String> order = ToolSet.missing(
                CONFIG,
                Map.of(),
                Set.of("minecraft:wooden_axe", "minecraft:stone_axe"),
                Set.of("minecraft:wooden_axe"));

        assertEquals(
                List.of("minecraft:wooden_axe"),
                order,
                "INC-86: топора нет, каменный — только через передачу со склада: сразу деревянный из своих брёвен, "
                        + "иначе дух рубит руками, пока стоит передача");
    }

    @Test
    void missing_WoodenAxeInHand_UpgradesToStone() {
        List<String> order = ToolSet.missing(
                CONFIG, Map.of("wooden_axe", 1), Set.of("minecraft:stone_axe"), Set.of("minecraft:wooden_axe"));

        assertEquals(
                List.of("minecraft:stone_axe"),
                order,
                "С деревянным топором набор улучшается до уровня tier: следующий заказ — каменный");
    }

    @Test
    void allowed_StoneAxeOrderStalled_WoodenFromOwnLogsAllowed() {
        BoardTicket stone = ticket("stock:craft_stone_axe:" + SELF, BoardCase.CRAFT_STONE_AXE, Optional.of(SELF));

        assertEquals(
                List.of(Set.of(), Set.of("minecraft:wooden_axe")),
                List.of(
                        ToolFallback.allowed(Set.of("minecraft:wooden_axe"), List.of(stone), 10, 1200),
                        ToolFallback.allowed(Set.of("minecraft:wooden_axe"), List.of(stone), 1200, 1200)),
                "Пока заказ каменного топора свеж, брёвна не тратятся на деревянный (их ждёт стройка); застрял "
                        + "дольше toolFallbackTicks — сразу деревянный из своих брёвен, потом улучшение");
    }

    @Test
    void order_WoodenAxeTicketAndChopping_SpiritCraftsAxeBeforeChopping() {
        Board board = new Board(() -> 0);
        board.submit(ticket("gather:jungle_log", BoardCase.GATHER_MATERIALS, Optional.empty()));
        board.submit(ticket("stock:craft_wooden_axe:" + SELF, BoardCase.CRAFT_WOODEN_AXE, Optional.of(SELF)));
        DispatchRound round = new DispatchRound(board, 0);
        DispatchRound.Candidate self =
                new DispatchRound.Candidate(SELF, "spirit-1", null, List.of("jungle_log"), FED, SpiritView.free());

        BoardTicket first = DispatchRound.order(board.claimable(new StockView(Map.of("jungle_log", 8)), 0)).stream()
                .filter(ticket -> round.fits(ticket, self))
                .findFirst()
                .orElseThrow();

        assertEquals(
                BoardCase.CRAFT_WOODEN_AXE,
                first.boardCase(),
                "Свой заказ топора идёт раньше рубки: сначала сделать инструмент из своих брёвен, потом рубить им");
    }

    private static BoardTicket ticket(String id, BoardCase boardCase, Optional<UUID> assignee) {
        return new BoardTicket(
                id,
                boardCase,
                assignee,
                BoardRules.priorityOf(boardCase),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
    }
}
