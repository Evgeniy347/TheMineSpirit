package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossActions.board;
import static com.theminespirit.core.board.graph.CrossActions.boardTicket;
import static com.theminespirit.core.board.graph.CrossActions.chopTarget;
import static com.theminespirit.core.board.graph.CrossActions.id;
import static com.theminespirit.core.board.graph.CrossActions.scripts;
import static com.theminespirit.core.board.graph.CrossScene.FED;
import static com.theminespirit.core.board.graph.CrossScene.damaged;
import static com.theminespirit.core.board.graph.CrossScene.facts;
import static com.theminespirit.core.board.graph.CrossScene.ids;
import static com.theminespirit.core.board.graph.CrossScene.named;
import static com.theminespirit.core.board.graph.CrossScene.plan;
import static com.theminespirit.core.board.graph.CrossScene.spirit;
import static com.theminespirit.core.board.graph.CrossScene.stageOne;
import static com.theminespirit.core.board.graph.CrossScene.task;
import static com.theminespirit.core.board.graph.CrossScene.ticketFor;
import static com.theminespirit.core.board.graph.CrossScene.tickets;
import static com.theminespirit.core.board.graph.CrossScene.unknown;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.SpiritView;
import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CrossRouteStageScenarioTests {
    private static final BlockCoord PIT = new BlockCoord(2, 60, 2);

    @Test
    void pit_WaterAtBottom_BridgeWaitsForTraceAndThenUsesPlanks() {
        GraphWorld world = stageOne();
        world.built("hut", Built.PLANNED);
        world.with("oak_planks", 64).with("oak_log", 64).with("cobblestone", 64);
        GraphFacts facts = facts(
                named(spirit("spirit-1"), spirit("spirit-2", 20, 20, true, false, "water", List.of(), List.of())),
                List.of(),
                List.of(),
                GraphSiteFacts.Terrain.empty(),
                Map.of());
        GraphPlan first = plan(world, facts);

        GraphPlan.Task bridge = task(first, "bridge");
        GraphPlan.Task trace = task(first, "restore_trace");
        assertFalse(bridge.feasible(), "Мост из ямы с водой не ставится, пока не возвращён след: " + ids(first));
        assertTrue(trace.feasible(), "Граф сначала возвращает след restore_trace: " + ids(first));
        assertTrue(
                first.task("building:hut").filter(GraphPlan.Task::feasible).isPresent(),
                "Стройка дома ждёт, но не отменяется: " + ids(first));

        world.with("trail_clean", 1);
        GraphPlan second = plan(world, facts);
        GraphPlan.Task ready = task(second, "bridge");
        assertTrue(ready.feasible(), "След возвращён — мост из ямы становится выполним: " + ids(second));
        assertEquals(
                Map.of("#planks", 8),
                ready.consumes(),
                "Мост тратит восемь досок, и это видно графу как конкуренцию со стройкой: " + ready);
        assertEquals(
                BoardCase.ACTIVITY_MOVE,
                BoardActivities.caseOfScript("bridge").orElseThrow(),
                "Мост — движение с изменением мира узла bridge, а не стройка дома");
        assertEquals(
                BoardCase.ACTIVITY_MOVE,
                BoardActivities.caseOfScript("goto").orElseThrow(),
                "Подход к яме остаётся обычным движением без ломания мира");
    }

    @Test
    void chunks_UnknownLot_NoBlindRebuildButKnownRepairStays() {
        GraphWorld world = GraphWorld.settled();
        for (String building : List.of("lighting", "streets", "orchard_oak")) {
            world.built(building, Built.BUILT);
        }
        world.built("fence", Built.PLANNED);
        world.built("hut", Built.PLANNED);
        world.with("oak_log", 64).with("oak_planks", 64).with("cobblestone", 64);
        GraphFacts facts = facts(
                named(spirit("spirit-1"), spirit("spirit-2")),
                List.of(),
                List.of(),
                GraphSiteFacts.Terrain.empty(),
                Map.of(
                        "hut", unknown("hut"),
                        "fence", damaged("fence", 40, 3, Map.of("oak_fence", 5))));
        GraphPlan plan = plan(world, facts);

        assertFalse(
                plan.task("building:hut").filter(GraphPlan.Task::feasible).isPresent(),
                "Незагруженный чанк — «неизвестно», а не «постройки нет»: дом не перестраивают вслепую: " + ids(plan));
        assertFalse(
                plan.task("event:repair_hut:repair:hut")
                        .filter(GraphPlan.Task::feasible)
                        .isPresent(),
                "Ремонт неизвестной постройки тоже не ставится: " + ids(plan));
        assertTrue(
                plan.task("event:repair_fence:repair:fence")
                        .filter(GraphPlan.Task::feasible)
                        .isPresent(),
                "Известный побитый забор чинится рядом с неизвестным домом: " + ids(plan));
        assertTrue(
                plan.task("mine_coal").filter(GraphPlan.Task::feasible).isPresent(),
                "Остальные работы не пропадают из-за одного неизвестного участка: " + ids(plan));
    }

    @Test
    void pit_StuckSpirit_RescuePostedAndOrdinaryWorkRefused() {
        GraphWorld world = stageOne();
        world.with("oak_log", 0).with("oak_planks", 0);
        GraphFacts facts = facts(
                named(spirit("spirit-1"), spirit("spirit-2", 20, 20, true, true, null, List.of(), List.of())),
                List.of(),
                List.of(),
                new GraphSiteFacts.Terrain(2, 0, 0, 2, 0, Map.of(), Map.of("minecraft:dirt", 2), Map.of()),
                Map.of());
        GraphPlan plan = plan(world, facts);
        GraphTickets.Result result = tickets(world, facts);

        assertTrue(
                plan.task("rescue_spirit").filter(GraphPlan.Task::feasible).isPresent(),
                "Спасение в графе: " + ids(plan));
        assertTrue(
                result.post().stream().anyMatch(ticket -> ticket.id().startsWith("graph:rescue_spirit")),
                "Спасение стоит на доске: "
                        + result.post().stream().map(GraphTickets.Ticket::id).toList());

        Board board = board(world, result);
        board.stuck().see(id("spirit-2"), PIT, board.now());
        board.stuck().mark(id("spirit-2"), board.now(), "яма: выхода нет");
        GraphTickets.Ticket chop = result.post().stream()
                .filter(ticket -> ticket.node().startsWith("chop_"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("В сцене нет рубки: " + result.post()));

        DispatchRound round = new DispatchRound(board, board.now());
        DispatchRound.Candidate stuck = new DispatchRound.Candidate(
                id("spirit-2"),
                "spirit-2",
                null,
                List.of("minecraft:stone_axe"),
                FED,
                SpiritView.free(),
                false,
                List.of());
        assertTrue(
                round.choose(boardTicket(chop), List.of(stuck)).isEmpty(),
                "Застрявший дух не получает обычную рубку, пока не вышел: "
                        + round.why(boardTicket(chop), List.of(stuck)));
        Optional<String> why = round.refusal(boardTicket(chop), stuck);
        assertTrue(
                why.isPresent() && why.orElseThrow().contains("не двигается"),
                "Причина отказа называет застревание, а не молчит: " + why);

        DispatchRound.Candidate helper = new DispatchRound.Candidate(
                id("spirit-1"),
                "spirit-1",
                null,
                List.of("minecraft:stone_pickaxe"),
                FED,
                SpiritView.free(),
                false,
                List.of());
        assertTrue(
                round.choose(boardTicket(ticketFor(result, "graph:rescue_spirit")), List.of(helper))
                        .isPresent(),
                "Свободный второй дух берёт спасение застрявшего");
        assertEquals(
                BoardCase.RESCUE_SPIRIT,
                plan.task("rescue_spirit").orElseThrow().boardCase(),
                "Спасение остаётся отдельным кейсом, а не обычной работой");
    }

    @Test
    void chop_HangingCanopyAfterChop_ClearFloatingAndHouseStayPosted() {
        GraphWorld world = stageOne();
        world.built("hut", Built.PLANNED);
        world.with("oak_log", 0).with("oak_planks", 0).with("dirt", 64);
        GraphFacts facts = facts(
                named(spirit("spirit-1"), spirit("spirit-2")),
                List.of(),
                List.of(),
                new GraphSiteFacts.Terrain(
                        2, 6, 0, 2, 0, Map.of(), Map.of("minecraft:dirt", 4), Map.of("minecraft:oak_leaves", 6)),
                Map.of());
        GraphPlan plan = plan(world, facts);

        GraphPlan.Task chop = task(plan, "chop_oak_log");
        GraphPlan.Task floating = task(plan, "event:clear_floating:clear_floating");
        GraphPlan.Task backfill = task(plan, "event:backfill:backfill");
        GraphPlan.Task house = task(plan, "building:hut");

        assertTrue(chop.feasible(), "Брёвен нет — рубка на доске: " + ids(plan));
        assertTrue(floating.feasible(), "Висящая крона после рубки получает уборку: " + ids(plan));
        assertTrue(backfill.feasible(), "Яма на участке засыпается рядом с уборкой кроны: " + ids(plan));
        assertTrue(house.feasible(), "Стройка дома не отменяется уборкой висящего: " + ids(plan));
        assertFalse(GraphStageGate.lotsReady(facts), "Яма на участке не даёт этапу закрыться, пока засыпка не сделана");
        assertTrue(
                scripts(BoardCase.GATHER_MATERIALS, chopTarget()).contains("chop_tree"),
                "Рубка валит дерево целиком скриптом chop_tree: " + scripts(BoardCase.GATHER_MATERIALS, chopTarget()));
        assertTrue(
                scripts(BoardCase.CLEAR_GROUNDS).contains("clear_area"),
                "Уборка кроны ломает блоки скриптом clear_area: " + scripts(BoardCase.CLEAR_GROUNDS));
        assertTrue(
                scripts(BoardCase.BUILD_HOUSE).contains("build_schematic"),
                "Дом строится схемой build_schematic: " + scripts(BoardCase.BUILD_HOUSE));
    }
}
