package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.AllocationCases.Pair;
import static com.theminespirit.core.board.graph.AllocationCases.boardTicket;
import static com.theminespirit.core.board.graph.AllocationCases.community;
import static com.theminespirit.core.board.graph.AllocationCases.dispatch;
import static com.theminespirit.core.board.graph.AllocationCases.facts;
import static com.theminespirit.core.board.graph.AllocationCases.id;
import static com.theminespirit.core.board.graph.AllocationCases.noChestFacts;
import static com.theminespirit.core.board.graph.AllocationCases.only;
import static com.theminespirit.core.board.graph.AllocationCases.spirit;
import static com.theminespirit.core.board.graph.AllocationCases.tickets;
import static com.theminespirit.core.board.graph.AllocationCases.tools;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.SupplyGate;
import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphAllocationGroupCTests {
    @Test
    void case17_MaterialInChest_SupplyNotGather() {
        GraphWorld world = community(1).with("cobblestone", 160).with("oak_log", 64);
        world.materials.put("hut", Map.of("cobblestone", 100, "oak_log", 20));
        GraphTickets.Result result = tickets(world);
        SupplyGate.Decision decision = SupplyGate.decide(
                Map.of("cobblestone", 100),
                Map.of("cobblestone", 160),
                64,
                Map.of(),
                0,
                BoardTuning.FAIL_BACKOFF_TICKS);

        assertTrue(
                only(result, "mine_cobble").isEmpty(),
                "Булыжник есть на складе — добыча камня не ставится: " + result.post());
        assertEquals(
                List.of(new SupplyGate.Request("cobblestone", 100)),
                decision.supply(),
                "Материал со склада несут на участок, а не добывают заново: " + decision);
        assertTrue(decision.gather().isEmpty(), "Добыча не ставится, пока склад отдаёт материал");
    }

    @Test
    void case18_EnoughDirtInChests_NoDigging() {
        GraphWorld world = community(2).with("dirt", 64).with("oak_log", 64).with("oak_planks", 64);
        world.graph.buildings().values().forEach(building -> world.buildings.put(building.id(), Built.BUILT));
        world.noWorld("tree:oak").noWorld("tree:jungle");
        GraphTickets.Result result = tickets(world);

        assertTrue(
                only(result, "dig_dirt").isEmpty(),
                "Земли в сундуках хватает — добыча земли не ставится (живой случай: сундуки полны земли): "
                        + result.post());
    }

    @Test
    void case19_Shortage_DigExactlyMissing() {
        GraphWorld world = community(1).with("dirt", 20).with("oak_log", 64).with("oak_planks", 64);
        world.graph.buildings().values().forEach(building -> world.buildings.put(building.id(), Built.BUILT));
        world.noWorld("tree:oak").noWorld("tree:jungle");
        GraphTickets.Result result = tickets(world);

        GraphTickets.Ticket dig = only(result, "dig_dirt").get(0);
        int threshold = GraphLack.threshold(world.graph.items().get("dirt"), world.state());
        int yield = world.graph.way("dig_dirt").orElseThrow().yield();
        int expected = Math.max(yield, threshold - 20);

        assertEquals(
                expected,
                dig.count(),
                "Нехватка земли закрывается недостающим (порог " + threshold + " − 20), а не всем порогом: "
                        + result.post());
    }

    @Test
    void case20_DigClosedShortageReturns_Reopens() {
        GraphWorld world = community(1).with("dirt", 0);
        GraphTickets.Ticket dig = only(tickets(world), "dig_dirt").get(0);
        Board board = new Board(() -> 0);
        board.submit(boardTicket(dig));
        board.claim(dig.id(), id("spirit-1"), 0);
        board.complete(dig.id());
        assertEquals(BoardStatus.DONE, board.statusOf(dig.id()).orElseThrow(), "Добыча закрыта первым прогоном");

        assertTrue(board.reopen(dig.id()), "Нехватка вернулась — закрытая добыча открывается заново");
        assertEquals(
                BoardOutcome.ACCEPTED,
                board.submit(boardTicket(dig)),
                "После переоткрытия граф ставит новую добычу, а не получает дубликат");
        assertEquals(
                List.of(new Pair("spirit-1", dig.id())),
                dispatch(board, world),
                "Переоткрытую добычу снова получает дух: " + board.statusOf(dig.id()));
    }

    @Test
    void case21_ChestsFull_NewChestBeforeMoreDigging() {
        GraphWorld world = community(1).with("dirt", 0);
        world.graph.buildings().values().forEach(building -> world.buildings.put(building.id(), Built.BUILT));
        world.buildings.put("streets", Built.PLANNED);
        world.storageFree = 5;
        GraphTickets.Result result = tickets(world);

        assertTrue(
                result.post().stream().anyMatch(ticket -> "graph:store_space".equals(ticket.id())),
                "Сундуки полны — на доске новая пара сундуков: " + result.post());
        assertTrue(
                result.post().stream()
                        .anyMatch(ticket -> "store:chests".equals(ticket.task().makes())),
                "Заявка новой пары сундуков доходит до доски как работа: " + result.post());
    }

    @Test
    void case22_CarriedGatheredGoods_DeliveredToChest() {
        GraphWorld world = community(1);
        world.facts = facts(
                List.of(new GraphFacts.Spirit(
                        "spirit-1",
                        new GraphFacts.Position(0, 64, 0),
                        20,
                        20,
                        300,
                        false,
                        false,
                        false,
                        null,
                        Map.of("minecraft:oak_log", 5),
                        List.of(),
                        tools("axe", 1))),
                Map.of("minecraft:cobblestone", 10));
        GraphTickets.Result result = tickets(world);

        assertTrue(
                result.post().stream().anyMatch(ticket -> ticket.id().contains("store_carry_deliver")),
                "Дух несёт добытое — на доске сдача на склад, а не «оставить в инвентаре»: " + result.post());
    }

    @Test
    void case27_HugeShortage_DigCountCapped() {
        GraphWorld world = community(1).with("dirt", 0);
        world.graph.buildings().values().forEach(building -> world.buildings.put(building.id(), Built.BUILT));
        world.buildings.put("streets", Built.PLANNED);
        world.materials.put("streets", Map.of("dirt", 400));
        world.noWorld("tree:oak").noWorld("tree:jungle");
        GraphTickets.Result result = tickets(world);

        GraphTickets.Ticket dig = result.post().stream()
                .filter(ticket -> "dig_dirt".equals(ticket.node()))
                .findFirst()
                .orElseThrow();

        assertTrue(dig.count() <= GraphTickets.COUNT_MAX, "Одна заявка добычи не больше стака: " + dig);
        assertEquals(
                GraphTickets.COUNT_MAX,
                dig.count(),
                "Нехватка 400 земли закрывается заявкой на стак, остальное — следующими: " + dig);
    }

    @Test
    void case28_MixedChest_SortingRaised() {
        GraphWorld world = community(1);
        world.facts = facts(
                List.of(spirit("spirit-1", List.of())), Map.of("minecraft:oak_log", 10, "minecraft:cobblestone", 10));
        GraphTickets.Result result = tickets(world);

        assertTrue(
                result.post().stream().anyMatch(ticket -> "graph:store_sort".equals(ticket.id())),
                "В сундуке дерево и камень вместе — сортировка стоит до новых запасов: " + result.post());
    }

    @Test
    void case29_CarriedButNoFreeChest_NoDeliverTask() {
        GraphWorld world = community(1);
        world.facts = noChestFacts(List.of(new GraphFacts.Spirit(
                "spirit-1",
                new GraphFacts.Position(0, 64, 0),
                20,
                20,
                300,
                false,
                false,
                false,
                null,
                Map.of("minecraft:oak_log", 5),
                List.of(),
                tools("axe", 1))));
        GraphTickets.Result result = tickets(world);

        assertFalse(
                result.post().stream().anyMatch(ticket -> ticket.id().contains("store_carry_deliver")),
                "Сдавать некуда — заявка сдачи не ставится: " + result.post());
    }
}
