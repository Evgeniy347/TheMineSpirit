package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossActions.board;
import static com.theminespirit.core.board.graph.CrossActions.boardTicket;
import static com.theminespirit.core.board.graph.CrossActions.candidates;
import static com.theminespirit.core.board.graph.CrossActions.chopTarget;
import static com.theminespirit.core.board.graph.CrossActions.id;
import static com.theminespirit.core.board.graph.CrossActions.scripts;
import static com.theminespirit.core.board.graph.CrossScene.FED;
import static com.theminespirit.core.board.graph.CrossScene.HUNGRY;
import static com.theminespirit.core.board.graph.CrossScene.chest;
import static com.theminespirit.core.board.graph.CrossScene.damaged;
import static com.theminespirit.core.board.graph.CrossScene.facts;
import static com.theminespirit.core.board.graph.CrossScene.ids;
import static com.theminespirit.core.board.graph.CrossScene.named;
import static com.theminespirit.core.board.graph.CrossScene.plan;
import static com.theminespirit.core.board.graph.CrossScene.spirit;
import static com.theminespirit.core.board.graph.CrossScene.stageOne;
import static com.theminespirit.core.board.graph.CrossScene.task;
import static com.theminespirit.core.board.graph.CrossScene.tasksFor;
import static com.theminespirit.core.board.graph.CrossScene.tasksMaking;
import static com.theminespirit.core.board.graph.CrossScene.threat;
import static com.theminespirit.core.board.graph.CrossScene.ticketFor;
import static com.theminespirit.core.board.graph.CrossScene.tickets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.SpiritView;
import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CrossLifeWorkScenarioTests {
    @Test
    void chop_ThreatAtChopper_DefendAtCeilingAndChopStaysPosted() {
        GraphWorld world = stageOne();
        world.with("oak_log", 0).with("oak_planks", 0);
        GraphFacts facts = facts(
                named(spirit("spirit-1", 4, 20), spirit("spirit-2")),
                List.of(
                        threat("mob-a", "spirit-1", GraphFacts.TargetKind.SPIRIT, 1),
                        threat("mob-b", "spirit-1", GraphFacts.TargetKind.SPIRIT, 2)),
                List.of(),
                GraphSiteFacts.Terrain.empty(),
                Map.of());
        GraphPlan plan = plan(world, facts);
        GraphPlan.Task defend = task(plan, "event:life_threat:defend");
        List<String> chops = tasksMaking(plan, "oak_log");
        assertEquals(
                100,
                defend.priority(),
                "Раненый дух под двумя мобами обязан получить защиту жизни на потолке графа: " + ids(plan));
        assertFalse(
                chops.isEmpty(), "Угроза не отменяет рубку: работа остаётся на доске рядом с защитой: " + ids(plan));
        assertTrue(
                defend.priority() > task(plan, chops.get(0)).priority(),
                "Защита жизни выше рубки: дух сначала отбивается, потом валит лес: " + ids(plan));
        assertEquals(
                defend.id(),
                tickets(world, facts).post().get(0).task().id(),
                "Первым на доску ставится defend, а не дальняя рубка: "
                        + tickets(world, facts).post().stream()
                                .map(GraphTickets.Ticket::id)
                                .toList());
        assertEquals(
                BoardCase.ACTIVITY_DEFEND,
                BoardActivities.caseOfScript("defend").orElseThrow(),
                "Защита выполняется скриптом defend, а не новой выдуманной механикой");
        assertTrue(
                scripts(BoardCase.GATHER_MATERIALS, chopTarget()).contains("chop_tree"),
                "После защиты механика рубки валит дерево скриптом chop_tree, а не ждёт опасность: "
                        + scripts(BoardCase.GATHER_MATERIALS, chopTarget()));
    }

    @Test
    void pit_InPlanCell_EscapeAndRescuePostedBesideRepairAndBackfill() {
        GraphWorld world = stageOne();
        world.with("oak_log", 64).with("oak_planks", 64).with("dirt", 64).with("cobblestone", 64);
        GraphFacts facts = facts(
                named(spirit("spirit-1"), spirit("spirit-2", 20, 20, true, true, null, List.of(), List.of())),
                List.of(),
                List.of(),
                new GraphSiteFacts.Terrain(2, 0, 0, 2, 0, Map.of(), Map.of("minecraft:dirt", 4), Map.of()),
                Map.of("hut", damaged("hut", 20, 0, Map.of("oak_log", 20))));
        GraphPlan plan = plan(world, facts);

        GraphPlan.Task rescue = task(plan, "rescue_spirit");
        GraphPlan.Task repair = task(plan, "event:repair_hut:repair:hut");
        GraphPlan.Task backfill = task(plan, "event:backfill:backfill");
        GraphPlan.Task escape = task(plan, "dig_out");

        assertTrue(rescue.feasible(), "Застрявший дух получает спасателя: " + ids(plan));
        assertTrue(repair.feasible(), "Повреждённая хижина не забыта из-за спасателя: " + ids(plan));
        assertTrue(backfill.feasible(), "Яма на участке засыпается параллельно спасению: " + ids(plan));
        assertTrue(escape.feasible(), "Дух в клетке получает собственный выход dig_out: " + ids(plan));
        assertTrue(rescue.priority() > repair.priority(), "Спасение жизни выше ремонта хижины: " + ids(plan));
        assertEquals(
                BoardCase.ACTIVITY_ESCAPE,
                BoardActivities.caseOfScript("dig_out").orElseThrow(),
                "Выход из клетки — действие escape, а не стройка и не добыча");
        assertTrue(
                scripts(BoardCase.RESCUE_SPIRIT).contains("clear_area"),
                "Спасатель разбирает клетку скриптом clear_area: " + scripts(BoardCase.RESCUE_SPIRIT));
        assertTrue(
                scripts(BoardCase.REPAIR_BUILDING).contains("build_schematic"),
                "Ремонт хижины идёт схемой build_schematic, а не засыпкой: " + scripts(BoardCase.REPAIR_BUILDING));
    }

    @Test
    void flee_WhileBuilding_GraphPostsRetreatButDispatchKeepsBuilderBusy() {
        GraphWorld world = stageOne();
        world.with("oak_log", 64).with("oak_planks", 64).with("cobblestone", 64);
        world.built("hut", Built.PLANNED);
        GraphFacts calm = facts(named(spirit("spirit-1"), spirit("spirit-2")));
        GraphTickets.Result calmTickets = tickets(world, calm);
        GraphTickets.Ticket build = calmTickets.post().stream()
                .filter(ticket -> ticket.id().startsWith("graph:building:hut"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Нет стройки хижины: " + calmTickets.post()));

        Board board = new Board(() -> 0);
        board.submit(boardTicket(build));
        board.claim(build.id(), id("spirit-2"), 0);

        GraphFacts danger = facts(
                named(spirit("spirit-1"), spirit("spirit-2", 4, 20)),
                List.of(
                        threat("mob-a", "spirit-2", GraphFacts.TargetKind.SPIRIT, 1),
                        threat("mob-b", "spirit-2", GraphFacts.TargetKind.SPIRIT, 2)),
                List.of(),
                GraphSiteFacts.Terrain.empty(),
                Map.of());
        GraphTickets.Result dangerTickets = tickets(world, danger);
        GraphTickets.Ticket retreat = dangerTickets.post().stream()
                .filter(ticket -> ticket.id().endsWith(":retreat"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Нет бегства в графе: " + dangerTickets.post()));
        board.submit(boardTicket(retreat));

        DispatchRound.Candidate builder = new DispatchRound.Candidate(
                id("spirit-2"), "spirit-2", "build_schematic", List.of(), FED, SpiritView.free(), false, List.of());
        DispatchRound round = new DispatchRound(board, 0);
        Optional<String> refusal = round.refusal(boardTicket(retreat), builder);

        assertEquals(
                BoardStatus.ASSIGNED,
                board.statusOf(build.id()).orElseThrow(),
                "Стройка остаётся за духом: граф не снимает метку работы при угрозе");
        assertTrue(refusal.isPresent(), "Занятый стройкой дух не берёт бегство вторым заданием: " + refusal);
        assertTrue(
                refusal.orElseThrow().contains("занят"),
                "Причина отказа называет занятость стройкой, а не молчит: " + refusal);
        assertTrue(
                round.choose(boardTicket(retreat), List.of(builder)).isEmpty(),
                "Дух не бросает стройку ради бегства: графу это не по силам, решает координатор");
    }

    @Test
    void night_FarChopAndNearBuild_NoDistantChoppingButBuildStays() {
        GraphWorld world = stageOne();
        world.with("oak_log", 0).with("oak_planks", 0).with("cobblestone", 64);
        world.built("hut", Built.PLANNED);
        world.distances.put("tree:oak", 80);
        world.distances.put("tree:jungle", 80);
        world.event("night");
        GraphFacts facts = facts(named(spirit("spirit-1"), spirit("spirit-2")));
        GraphPlan plan = plan(world, facts);

        assertTrue(
                plan.tasks().stream().noneMatch(task -> task.id().startsWith("chop_") && task.feasible()),
                "Ночью граф не отправляет духа за далёким деревом: " + ids(plan));
        assertTrue(
                plan.task("building:hut").filter(GraphPlan.Task::feasible).isPresent(),
                "Ночью ближняя стройка на участке остаётся работой: " + ids(plan));
        assertTrue(
                tasksFor(plan, BoardCase.ACTIVITY_GUARD).size() >= 2,
                "Ночью граф ставит охрану поселения и сундуков: " + ids(plan));
        assertTrue(
                scripts(BoardCase.BUILD_HOUSE).contains("build_schematic"),
                "Стройка выполняется схемой build_schematic: " + scripts(BoardCase.BUILD_HOUSE));
        assertEquals(
                BoardCase.ACTIVITY_MOVE,
                BoardActivities.caseOfScript("walk_careful").orElseThrow(),
                "Далёкий маршрут ночью — отдельное осторожное движение, а не рубка");
    }

    @Test
    void hunger_NoFoodInStock_GraphSendsHungrySpiritToMine() {
        GraphWorld world = stageOne();
        world.tools("spirit-2", 1);
        world.with("coal", 0).with("food", 0).event("hungry");
        GraphFacts facts = facts(named(spirit("spirit-1", 20, 2), spirit("spirit-2")));
        GraphPlan plan = plan(world, facts);
        GraphPlan.Task eat = task(plan, "event:hungry:eat");
        GraphPlan.Task mine = task(plan, "mine_coal");

        assertFalse(eat.feasible(), "Еды нет ни в руках, ни на складе — поесть нечем: " + ids(plan));
        assertTrue(mine.feasible(), "Шахта остаётся выполнимой работой: " + ids(plan));
        assertFalse(
                tickets(world, facts).post().stream()
                        .anyMatch(ticket -> ticket.id().endsWith(":eat")),
                "Невыполнимая еда не ставится на доску, и голодный дух остаётся без неё");
        assertEquals(
                mine.id(),
                tickets(world, facts).post().get(0).task().id(),
                "Голодный дух получает шахту первым заданием, хотя еда важнее: "
                        + tickets(world, facts).post().stream()
                                .map(GraphTickets.Ticket::id)
                                .toList());

        List<DispatchRound.Candidate> candidates = candidates(
                world,
                Map.of("spirit-1", HUNGRY, "spirit-2", FED),
                Map.of("spirit-1", SpiritView.free(), "spirit-2", SpiritView.free()));
        DispatchRound round = new DispatchRound(board(world, tickets(world, facts)), 0);
        Optional<DispatchRound.Candidate> chosen =
                round.choose(boardTicket(ticketFor(tickets(world, facts), GraphTickets.id(mine))), candidates);
        assertEquals(
                "spirit-1",
                chosen.map(DispatchRound.Candidate::name).orElse(""),
                "Голодного духа отправляют копать уголь, а не к еде: " + candidates);
    }

    @Test
    void hunger_FoodInChest_EatOutranksMineAndGoesToHungrySpirit() {
        GraphWorld world = stageOne();
        world.tools("spirit-2", 1);
        world.with("coal", 0).with("food", 8).event("hungry");
        GraphFacts facts = facts(
                named(spirit("spirit-1", 20, 2), spirit("spirit-2")),
                List.of(),
                List.of(chest(Map.of("food", 8))),
                GraphSiteFacts.Terrain.empty(),
                Map.of());
        GraphPlan plan = plan(world, facts);
        GraphPlan.Task eat = task(plan, "event:hungry:eat");
        GraphPlan.Task mine = task(plan, "mine_coal");

        assertTrue(eat.feasible(), "Еда в сундуке делает поесть выполнимым: " + ids(plan));
        assertTrue(eat.priority() > mine.priority(), "Поесть важнее шахты: " + ids(plan));
        List<GraphTickets.Ticket> posted = tickets(world, facts).post();
        assertEquals(
                eat.id(),
                posted.get(0).task().id(),
                "Первым на доске поесть, а не копать: "
                        + posted.stream().map(GraphTickets.Ticket::id).toList());
        assertEquals(
                BoardCase.EAT_TO_FULL, eat.boardCase(), "Еда — внешний кейс, дух ест сам, доска только отдаёт задание");
        List<DispatchRound.Candidate> candidates = candidates(
                world,
                Map.of("spirit-1", HUNGRY, "spirit-2", FED),
                Map.of("spirit-1", SpiritView.free(), "spirit-2", SpiritView.free()));
        DispatchRound round = new DispatchRound(board(world, tickets(world, facts)), 0);
        assertEquals(
                "spirit-1",
                round.choose(boardTicket(ticketFor(tickets(world, facts), GraphTickets.id(eat))), candidates)
                        .map(DispatchRound.Candidate::name)
                        .orElse(""),
                "Поесть достаётся голодному духу, а не сытому");
    }

    @Test
    void night_ThreatAtWounded_DefendAboveSleepAndGuard() {
        GraphWorld world = stageOne();
        world.event("night");
        GraphFacts facts = facts(
                named(spirit("spirit-1", 4, 20), spirit("spirit-2")),
                List.of(
                        threat("mob-a", "spirit-1", GraphFacts.TargetKind.SPIRIT, 1),
                        threat("mob-b", "spirit-1", GraphFacts.TargetKind.SPIRIT, 2)),
                List.of(),
                GraphSiteFacts.Terrain.empty(),
                Map.of());
        GraphPlan plan = plan(world, facts);

        GraphPlan.Task defend = task(plan, "event:life_threat:defend");
        GraphPlan.Task sleep = task(plan, "event:life_threat:sleep");
        GraphPlan.Task guard = task(plan, "event:night:more4_guard_settlement");

        assertEquals(100, defend.priority(), "Ночной моб у раненого духа поднимает защиту на потолок: " + ids(plan));
        assertTrue(sleep.priority() < defend.priority(), "Сон не важнее боя с мобом: " + ids(plan));
        assertTrue(guard.priority() < defend.priority(), "Охрана поселения ниже защиты жизни: " + ids(plan));
        assertEquals(
                BoardCase.ACTIVITY_REST,
                BoardActivities.caseOfScript("sleep").orElseThrow(),
                "Сон выполняется кейсом отдыха, а не боем");
        assertEquals(
                BoardCase.ACTIVITY_GUARD,
                BoardActivities.caseOfScript("patrol").orElseThrow(),
                "Охрана выполняется патрулём, а не защитой конкретного духа");
    }
}
