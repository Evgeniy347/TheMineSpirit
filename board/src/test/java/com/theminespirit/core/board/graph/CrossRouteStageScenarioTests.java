package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossActions.*;
import static com.theminespirit.core.board.graph.CrossScene.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.SpiritView;
import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
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
    void pit_PillarNeedsTrailClean_SecondRunOpensExit() {
        GraphWorld world = stageOne();
        world.with("oak_planks", 64).with("cobblestone", 64).with("oak_log", 64);
        GraphFacts facts = facts(
                named(spirit("spirit-1"), spirit("spirit-2", 20, 20, true, false, "pit", List.of(), List.of())),
                List.of(),
                List.of(),
                GraphSiteFacts.Terrain.empty(),
                Map.of());
        GraphPlan first = plan(world, facts);

        assertFalse(
                task(first, "pillar").feasible(), "Столб из ямы не ставится, пока не возвращён след: " + ids(first));
        assertTrue(task(first, "restore_trace").feasible(), "Первый прогон даёт возврат следа: " + ids(first));

        world.with("trail_clean", 1);
        GraphPlan second = plan(world, facts);

        assertTrue(
                task(second, "pillar").feasible(),
                "След возвращён — выход столбом появляется со следующего прогона: " + ids(second));
        List<String> pillar = new ArrayList<>();
        collect(ActivityPlans.pillar(3, "oak_planks").task(), pillar);
        assertEquals("pillar_up", pillar.get(0), "Столб строится действием pillar_up, а не телепортом: " + pillar);
        assertEquals(
                BoardCase.ACTIVITY_BUILD,
                BoardActivities.caseOfScript("pillar_up").orElseThrow(),
                "Столб меняет мир отдельным узлом pillar, а не телепортом");
    }

    @Test
    void movement_WorkCards_MoveToTargetThenAct() {
        BlockCoord at = new BlockCoord(3, 64, 3);
        BlockCoord chest = new BlockCoord(1, 64, 1);

        assertMovesFirst("жатва", ActivityPlans.till(at).task(), "till");
        assertMovesFirst("посев", ActivityPlans.plantCrop(at, "wheat", 4).task(), "plant_crop");
        assertMovesFirst("кормление", ActivityPlans.feed(at, Species.SHEEP, 4).task(), "feed");
        assertMovesFirst("дойка", ActivityPlans.milk(at, 2).task(), "milk");
        assertMovesFirst("крафт", ActivityPlans.craft(at, "torch", 4).task(), "craft");
        assertMovesFirst(
                "броня", ActivityPlans.equip(chest, at, "iron_chestplate").task(), "equip");
        assertMovesFirst("склад", ActivityPlans.unload(at).task(), "unload");
        assertMovesFirst(
                "перенос", ActivityPlans.moveItem(chest, at, at, "bread", 1).task(), "deposit");
        assertMovesFirst("еда", ActivityPlans.withdraw(chest, at, "bread", 2).task(), "withdraw");
        assertMovesFirst("плавка", ActivityPlans.smelt(at, "iron_ore", 2).task(), "smelt");

        List<String> chop = scripts(BoardCase.GATHER_MATERIALS, chopTarget());
        assertEquals("goto", chop.get(0), "Рубка начинается с подхода к дереву: " + chop);
        assertTrue(chop.contains("chop_tree"), "Подошёл — валит дерево: " + chop);
        List<String> build = scripts(BoardCase.BUILD_HOUSE);
        assertEquals("goto", build.get(0), "Стройка начинается с подхода к участку: " + build);
        assertTrue(build.contains("build_schematic"), "На участке ставит схему: " + build);
        List<String> fence = scripts(BoardCase.REPAIR_FENCE);
        assertTrue(
                fence.contains("goto") && fence.contains("fill"),
                "Ремонт забора идёт к пролому и заделывает его: " + fence);
        List<String> light = scripts(BoardCase.LIGHTING);
        assertEquals("goto", light.get(0), "Свет начинается с подхода к тёмному месту: " + light);
        assertTrue(light.contains("place_block"), "На месте ставит факел: " + light);
        assertEquals(
                BoardCase.ACTIVITY_RETREAT,
                BoardActivities.caseOfScript("retreat").orElseThrow(),
                "Бегство остаётся действием отхода, а не рубкой");
        assertEquals(
                BoardCase.ACTIVITY_EXPEDITION,
                BoardActivities.caseOfScript("follow").orElseThrow(),
                "Поход идёт сопровождением follow");
    }

    private static void assertMovesFirst(String what, TaskNode plan, String action) {
        List<String> scripts = new ArrayList<>();
        collect(plan, scripts);
        assertEquals(
                "goto", scripts.get(0), what + " начинается с перехода к цели, а не с действия на месте: " + scripts);
        assertTrue(scripts.contains(action), what + " после перехода выполняет своё действие: " + scripts);
    }

    private static void collect(TaskNode node, List<String> scripts) {
        if (node instanceof ScriptCall call) {
            scripts.add(call.script());
            return;
        }
        if (node instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, scripts));
            return;
        }
        if (node instanceof FallbackNode fallback) {
            fallback.steps().forEach(step -> collect(step, scripts));
        }
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
}
