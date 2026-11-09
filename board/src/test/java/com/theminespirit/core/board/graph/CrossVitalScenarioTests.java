package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossActions.board;
import static com.theminespirit.core.board.graph.CrossActions.boardTicket;
import static com.theminespirit.core.board.graph.CrossActions.busy;
import static com.theminespirit.core.board.graph.CrossActions.candidates;
import static com.theminespirit.core.board.graph.CrossActions.id;
import static com.theminespirit.core.board.graph.CrossScene.FED;
import static com.theminespirit.core.board.graph.CrossScene.HUNGRY;
import static com.theminespirit.core.board.graph.CrossScene.facts;
import static com.theminespirit.core.board.graph.CrossScene.ids;
import static com.theminespirit.core.board.graph.CrossScene.named;
import static com.theminespirit.core.board.graph.CrossScene.plan;
import static com.theminespirit.core.board.graph.CrossScene.spirit;
import static com.theminespirit.core.board.graph.CrossScene.stageOne;
import static com.theminespirit.core.board.graph.CrossScene.stageTwo;
import static com.theminespirit.core.board.graph.CrossScene.task;
import static com.theminespirit.core.board.graph.CrossScene.ticketFor;
import static com.theminespirit.core.board.graph.CrossScene.tickets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.EatNeed;
import com.theminespirit.core.board.SpiritView;
import com.theminespirit.core.board.VitalGate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CrossVitalScenarioTests {
    @Test
    void vital_HungryOrInjured_OrdinaryWorkIsBlockedAndVitalChainPreempts() {
        GraphWorld world = stageOne();
        world.tools("spirit-2", 1);
        world.tools("spirit-3", 1);
        world.with("coal", 0).with("food", 0).event("hungry").event("more4_caution");
        GraphFacts facts = facts(named(spirit("spirit-1", 20, 4), spirit("spirit-2", 4, 20), spirit("spirit-3")));
        GraphPlan plan = plan(world, facts);
        GraphPlan.Task hunt = task(plan, "event:hungry:hunt");
        GraphPlan.Task wait = task(plan, "event:hungry:wait_food");
        GraphPlan.Task mine = task(plan, "mine_coal");
        int ceiling = GraphPriorities.work(GraphWorld.GRAPH);

        assertTrue(
                hunt.priority() >= ceiling - VitalNeeds.BAND,
                "Жизненная нужда держится в верхней полосе приоритетов, а не тонет среди обычных работ: охота "
                        + hunt.priority() + ", потолок " + ceiling);
        assertTrue(
                wait.priority() < hunt.priority(),
                "Внутри полосы нужды порядок сохраняется: ожидание " + wait.priority() + ", охота " + hunt.priority());
        assertTrue(
                hunt.priority() > mine.priority(),
                "Добыча еды выше шахты: охота " + hunt.priority() + ", шахта " + mine.priority());

        GraphTickets.Result result = tickets(world, facts);
        Board board = board(world, result);
        DispatchRound round = new DispatchRound(board, 0);
        List<DispatchRound.Candidate> candidates = candidates(
                world,
                Map.of(
                        "spirit-1", HUNGRY,
                        "spirit-2", new EatNeed(20, 4, 20, 20),
                        "spirit-3", FED),
                Map.of(
                        "spirit-1", SpiritView.free(),
                        "spirit-2", SpiritView.free(),
                        "spirit-3", SpiritView.free()));
        String mineId = GraphTickets.id(mine);
        DispatchRound.Candidate hungry = candidates.get(0);
        DispatchRound.Candidate injured = candidates.get(1);

        assertTrue(
                round.refusal(boardTicket(ticketFor(result, mineId)), hungry)
                        .orElse("")
                        .contains("голод"),
                "Голодному духу не выдаётся шахта, пока нужда жива: "
                        + round.refusal(boardTicket(ticketFor(result, mineId)), hungry));
        BoardTicket defend = new BoardTicket(
                "graph:event:life_threat:defend",
                BoardCase.ACTIVITY_DEFEND,
                Optional.empty(),
                100,
                0,
                1,
                "graph:event:life_threat:defend",
                List.of(),
                Map.of());
        assertTrue(
                round.refusal(defend, hungry).isEmpty(),
                "Защита жизни — исключение VitalGate: голодный дух всё равно защищается");
        assertFalse(
                VitalGate.blocksWork(defend, hungry.needs()),
                "Живая нужда не отменяет защиту жизни и не снимает её с духа");
        assertTrue(
                VitalGate.blocksWork(boardTicket(ticketFor(result, mineId)), hungry.needs()),
                "Обычная работа при живой нужде блокируется и снимается с духа");
        assertTrue(
                round.refusal(boardTicket(ticketFor(result, mineId)), injured)
                        .orElse("")
                        .contains("ранение"),
                "Раненому духу не выдаётся шахта, пока нужда жива: "
                        + round.refusal(boardTicket(ticketFor(result, mineId)), injured));
        assertEquals(
                "spirit-3",
                round.choose(boardTicket(ticketFor(result, mineId)), candidates)
                        .map(DispatchRound.Candidate::name)
                        .orElse(""),
                "Шахту получает только здоровый и сытый дух: " + candidates);

        board.claim(mineId, id("spirit-1"), 0);
        DispatchRound busy = new DispatchRound(board, 0);
        List<DispatchRound.Candidate> busyCandidates =
                List.of(busy(candidates.get(0), "mine_coal"), candidates.get(1), candidates.get(2));
        assertEquals(
                "spirit-1",
                busy.choose(boardTicket(ticketFor(result, GraphTickets.id(hunt))), busyCandidates)
                        .map(DispatchRound.Candidate::name)
                        .orElse(""),
                "Голодный дух бросает шахту ради еды, а не дорабатывает смену голодным: " + busyCandidates);
        assertEquals(
                List.of(mineId),
                busy.drainDisplaced(id("spirit-1")).stream().map(t -> t.id()).toList(),
                "Вытесненная шахта возвращается на доску, а не остаётся за голодным духом");
        assertEquals(
                BoardStatus.PENDING,
                board.statusOf(mineId).orElseThrow(),
                "Шахта после вытеснения снова ждёт свободного духа: " + board.statusOf(mineId));

        GraphWorld bare = stageOne().noWorld("animals").noWorld("water");
        bare.with("coal", 0).with("food", 0).event("hungry");
        GraphFacts bareFacts = facts(named(spirit("spirit-1", 20, 4), spirit("spirit-2")));
        GraphPlan barePlan = plan(bare, bareFacts);

        assertTrue(
                barePlan.notices().stream().anyMatch(notice -> notice.reason().contains("голод")),
                "Без еды и источника граф оставляет стратегу диагноз, а не молчит: " + barePlan.notices());
        assertTrue(
                barePlan.task("event:hungry:wait_food").stream().anyMatch(GraphPlan.Task::feasible),
                "Ждать еду — выполнимый запасной путь нужды: " + ids(barePlan));

        GraphWorld live =
                GraphMutations.liveStageOne().event("hungry").with("food", 0).tools("spirit-2", 1);
        GraphPlan livePlan = plan(live, live.facts);
        GraphTickets.Result liveResult = tickets(live, live.facts);
        DispatchRound liveRound = new DispatchRound(board(live, liveResult), 0);
        List<DispatchRound.Candidate> liveCandidates = candidates(
                live,
                Map.of("spirit-1", HUNGRY, "spirit-2", FED),
                Map.of("spirit-1", SpiritView.free(), "spirit-2", SpiritView.free()));
        GraphPlan.Task liveWork = task(livePlan, "dig_dirt");

        assertTrue(
                liveRound
                        .refusal(boardTicket(ticketFor(liveResult, GraphTickets.id(liveWork))), liveCandidates.get(0))
                        .orElse("")
                        .contains("голод"),
                "На живом снимке этапа 1 голодному духу тоже не достаётся обычная работа");
        assertEquals(
                "spirit-2",
                liveRound
                        .choose(boardTicket(ticketFor(liveResult, GraphTickets.id(liveWork))), liveCandidates)
                        .map(DispatchRound.Candidate::name)
                        .orElse(""),
                "На живом снимке обычную работу получает сытый дух: " + liveCandidates);
    }

    @Test
    void vital_StageTwoCoalEmpty_GateKeepsFoodOverSaturatedMine() {
        GraphWorld world = stageTwo();
        world.built("wheat_field", GraphState.Built.BUILT);
        world.tools("spirit-2", 1);
        world.with("coal", 0).with("food", 0).event("hungry");
        GraphFacts facts = facts(named(spirit("spirit-1", 20, 2), spirit("spirit-2")));
        GraphPlan plan = plan(world, facts);

        GraphPlan.Task hunt = task(plan, "event:hungry:hunt");
        GraphPlan.Task fish = task(plan, "event:hungry:fish");
        GraphPlan.Task harvest = task(plan, "event:hungry:harvest_crop");
        GraphPlan.Task wait = task(plan, "event:hungry:wait_food");
        GraphPlan.Task mine = task(plan, "mine_coal");
        int need = GraphPriorities.need(GraphWorld.GRAPH);

        assertTrue(harvest.feasible(), "Поле построено — урожай можно собрать: " + ids(plan));
        assertEquals(BoardCase.GATHER_MATERIALS, harvest.boardCase(), "Сбор урожая — работа фермы, а не охота");
        assertEquals(need, hunt.priority(), "Охота — верх полосы потребности: " + hunt.priority());
        assertTrue(
                hunt.priority() > mine.priority(),
                "Охота выше насыщенной шахты на этапе 2: охота " + hunt.priority() + ", шахта " + mine.priority());
        for (GraphPlan.Task food : List.of(hunt, fish, harvest, wait)) {
            assertTrue(
                    food.priority() >= need - VitalNeeds.BAND,
                    "Еда держится в полосе потребности: " + food.id() + " " + food.priority());
        }
        assertTrue(
                mine.priority() <= GraphPriorities.work(GraphWorld.GRAPH),
                "Шахта остаётся обычной работой под полосой потребности: шахта " + mine.priority());
        GraphTickets.Result result = tickets(world, facts);
        List<DispatchRound.Candidate> candidates = candidates(
                world,
                Map.of("spirit-1", HUNGRY, "spirit-2", FED),
                Map.of("spirit-1", SpiritView.free(), "spirit-2", SpiritView.free()));
        DispatchRound round = new DispatchRound(board(world, result), 0);
        assertTrue(
                round.refusal(boardTicket(ticketFor(result, GraphTickets.id(mine))), candidates.get(0))
                        .orElse("")
                        .contains("голод"),
                "VitalGate не отдаёт шахту голодному, пока есть путь к еде: "
                        + round.refusal(boardTicket(ticketFor(result, GraphTickets.id(mine))), candidates.get(0)));
        assertEquals(
                hunt.id(),
                result.post().get(0).task().id(),
                "Первым на доске добыть еду, а не копать: "
                        + result.post().stream().map(GraphTickets.Ticket::id).toList());
    }

    @Test
    void vital_HungryWithoutPathOnBoard_KeepsWorkingAndWorkIsNotBlocked() {
        GraphWorld world = GraphMutations.liveStageOne().with("food", 0).tools("spirit-2", 1);
        GraphPlan plan = plan(world, world.facts);
        GraphTickets.Result result = tickets(world, world.facts);
        DispatchRound round = new DispatchRound(board(world, result), 0);
        List<DispatchRound.Candidate> candidates = candidates(
                world,
                Map.of("spirit-1", HUNGRY, "spirit-2", FED),
                Map.of("spirit-1", SpiritView.free(), "spirit-2", SpiritView.free()));

        assertFalse(
                round.refusal(
                                boardTicket(ticketFor(result, GraphTickets.id(task(plan, "dig_dirt")))),
                                candidates.get(0))
                        .orElse("")
                        .contains("голод"),
                "Пути к еде на доске нет: простой её не добудет, дух работает, а граф поднимает источник");
    }
}
