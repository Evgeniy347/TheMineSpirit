package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossActions.board;
import static com.theminespirit.core.board.graph.CrossActions.boardTicket;
import static com.theminespirit.core.board.graph.CrossActions.candidates;
import static com.theminespirit.core.board.graph.CrossActions.id;
import static com.theminespirit.core.board.graph.CrossScene.FED;
import static com.theminespirit.core.board.graph.CrossScene.HUNGRY;
import static com.theminespirit.core.board.graph.CrossScene.chest;
import static com.theminespirit.core.board.graph.CrossScene.facts;
import static com.theminespirit.core.board.graph.CrossScene.ids;
import static com.theminespirit.core.board.graph.CrossScene.named;
import static com.theminespirit.core.board.graph.CrossScene.plan;
import static com.theminespirit.core.board.graph.CrossScene.spirit;
import static com.theminespirit.core.board.graph.CrossScene.stageOne;
import static com.theminespirit.core.board.graph.CrossScene.task;
import static com.theminespirit.core.board.graph.CrossScene.ticketFor;
import static com.theminespirit.core.board.graph.CrossScene.tickets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.SpiritView;
import com.theminespirit.core.board.api.BoardCase;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CrossHungerScenarioTests {
    @Test
    void hunger_NoFoodInStock_HungrySpiritGetsFoodNotMine() {
        GraphWorld world = stageOne();
        world.tools("spirit-2", 1);
        world.with("coal", 0).with("food", 0).event("hungry");
        GraphFacts facts = facts(named(spirit("spirit-1", 20, 2), spirit("spirit-2")));
        GraphPlan plan = plan(world, facts);

        GraphPlan.Task eat = task(plan, "event:hungry:eat");
        GraphPlan.Task hunt = task(plan, "event:hungry:hunt");
        GraphPlan.Task wait = task(plan, "event:hungry:wait_food");
        GraphPlan.Task mine = task(plan, "mine_coal");

        assertFalse(
                eat.feasible(),
                "Еды нет ни в руках, ни на складе — поесть нечем: " + ids(plan)
                        + " Иначе тест hunger_NoFoodInStock_HungrySpiritGetsFoodNotMine перестанет ловить эту поломку. При смене hunger_NoFoodInStock_HungrySpiritGetsFoodNotMine проверь это утверждение.");
        assertTrue(
                hunt.feasible(),
                "Рядом животные — голодный дух идёт добывать еду охотой: " + ids(plan)
                        + " Иначе тест hunger_NoFoodInStock_HungrySpiritGetsFoodNotMine перестанет ловить эту поломку. При смене hunger_NoFoodInStock_HungrySpiritGetsFoodNotMine проверь это утверждение.");
        assertTrue(
                hunt.priority() > mine.priority(),
                "Добыча еды выше шахты, пока еды нет и дух голоден: охота " + hunt.priority() + ", шахта "
                        + mine.priority()
                        + " Иначе тест hunger_NoFoodInStock_HungrySpiritGetsFoodNotMine перестанет ловить эту поломку. При смене hunger_NoFoodInStock_HungrySpiritGetsFoodNotMine проверь это утверждение.");
        assertEquals(
                Optional.of("spirit-1"),
                hunt.spirit(),
                "Добычу еды граф адресует голодному духу, а не сытому: " + hunt
                        + " Иначе тест hunger_NoFoodInStock_HungrySpiritGetsFoodNotMine перестанет ловить эту поломку. При смене hunger_NoFoodInStock_HungrySpiritGetsFoodNotMine проверь это утверждение.");
        assertEquals(
                Optional.of("spirit-1"),
                wait.spirit(),
                "Ожидание еды тоже адресовано голодному духу: " + wait
                        + " Иначе тест hunger_NoFoodInStock_HungrySpiritGetsFoodNotMine перестанет ловить эту поломку. При смене hunger_NoFoodInStock_HungrySpiritGetsFoodNotMine проверь это утверждение.");
        assertTrue(
                wait.priority() > mine.priority(),
                "Ожидание еды выше шахты, пока еды нет: ожидание " + wait.priority() + ", шахта " + mine.priority()
                        + " Иначе тест hunger_NoFoodInStock_HungrySpiritGetsFoodNotMine перестанет ловить эту поломку. При смене hunger_NoFoodInStock_HungrySpiritGetsFoodNotMine проверь это утверждение.");
        assertTrue(
                order(plan, hunt.id()) < order(plan, wait.id()),
                "Ожидание — запасной путь после добычи: охота " + hunt.priority() + ", ожидание " + wait.priority()
                        + " Иначе тест hunger_NoFoodInStock_HungrySpiritGetsFoodNotMine перестанет ловить эту поломку. При смене hunger_NoFoodInStock_HungrySpiritGetsFoodNotMine проверь это утверждение.");
        assertTrue(
                tickets(world, facts).post().get(0).task().id().startsWith("event:hungry:"),
                "Первым на доске добыть еду, а не копать: "
                        + tickets(world, facts).post().stream()
                                .map(GraphTickets.Ticket::id)
                                .toList()
                        + " Иначе тест hunger_NoFoodInStock_HungrySpiritGetsFoodNotMine перестанет ловить эту поломку. При смене hunger_NoFoodInStock_HungrySpiritGetsFoodNotMine проверь это утверждение.");

        List<DispatchRound.Candidate> candidates = candidates(
                world,
                Map.of("spirit-1", HUNGRY, "spirit-2", FED),
                Map.of("spirit-1", SpiritView.free(), "spirit-2", SpiritView.free()));
        GraphTickets.Result sourceTickets = tickets(world, facts);
        DispatchRound sourceRound = new DispatchRound(board(world, sourceTickets), 0);
        assertEquals(
                "spirit-1",
                sourceRound
                        .choose(
                                boardTicket(
                                        ticketFor(sourceTickets, GraphTickets.id(hunt)), Optional.of(id("spirit-1"))),
                                candidates)
                        .map(DispatchRound.Candidate::name)
                        .orElse(""),
                "Добыть еду достаётся голодному духу: " + candidates
                        + " Иначе тест hunger_NoFoodInStock_HungrySpiritGetsFoodNotMine перестанет ловить эту поломку. При смене hunger_NoFoodInStock_HungrySpiritGetsFoodNotMine проверь это утверждение.");
        sourceRound.take(id("spirit-1"));
        assertEquals(
                "spirit-2",
                sourceRound
                        .choose(boardTicket(ticketFor(sourceTickets, GraphTickets.id(mine))), candidates)
                        .map(DispatchRound.Candidate::name)
                        .orElse(""),
                "Сытый дух тем временем получает шахту: " + candidates
                        + " Иначе тест hunger_NoFoodInStock_HungrySpiritGetsFoodNotMine перестанет ловить эту поломку. При смене hunger_NoFoodInStock_HungrySpiritGetsFoodNotMine проверь это утверждение.");

        assertWaterFish(facts);
        GraphWorld bare = assertBareWait(facts);
        assertLiveSnapshot();
        assertBareDispatch(bare, facts, candidates);
    }

    private static void assertWaterFish(GraphFacts facts) {
        GraphWorld watery = stageOne().noWorld("animals");
        watery.tools("spirit-2", 1);
        watery.with("coal", 0).with("food", 0).event("hungry");
        GraphPlan waterPlan = plan(watery, facts);
        GraphPlan.Task waterFish = task(waterPlan, "event:hungry:fish");
        GraphPlan.Task waterMine = task(waterPlan, "mine_coal");

        assertTrue(
                waterFish.feasible(),
                "Есть вода — голодный дух добывает еду рыбалкой: " + ids(waterPlan)
                        + " Иначе тест assertWaterFish перестанет ловить эту поломку. При смене assertWaterFish проверь это утверждение.");
        assertTrue(
                waterFish.priority() > waterMine.priority(),
                "Рыбалка выше шахты, пока еды нет: рыбалка " + waterFish.priority() + ", шахта " + waterMine.priority()
                        + " Иначе тест assertWaterFish перестанет ловить эту поломку. При смене assertWaterFish проверь это утверждение.");
        assertEquals(
                Optional.of("spirit-1"),
                waterFish.spirit(),
                "Рыбалка адресована голодному духу, а не сытому: " + waterFish
                        + " Иначе тест assertWaterFish перестанет ловить эту поломку. При смене assertWaterFish проверь это утверждение.");
        assertEquals(
                waterFish.id(),
                tickets(watery, facts).post().get(0).task().id(),
                "Первым на доске рыбалка, а не копать: "
                        + tickets(watery, facts).post().stream()
                                .map(GraphTickets.Ticket::id)
                                .toList()
                        + " Иначе тест assertWaterFish перестанет ловить эту поломку. При смене assertWaterFish проверь это утверждение.");
    }

    private static GraphWorld assertBareWait(GraphFacts facts) {
        GraphWorld bare = stageOne().noWorld("animals").noWorld("water");
        bare.tools("spirit-2", 1);
        bare.with("coal", 0).with("food", 0).event("hungry");
        GraphPlan barePlan = plan(bare, facts);

        assertTrue(
                barePlan.task("event:hungry:hunt").isEmpty(),
                "Нет животных — охота не источник еды и не ставится: " + ids(barePlan)
                        + " Иначе тест assertWaterFish перестанет ловить эту поломку. При смене assertWaterFish проверь это утверждение.");
        assertTrue(
                barePlan.task("event:hungry:fish").isEmpty(),
                "Нет воды — рыбалка не источник еды и не ставится: " + ids(barePlan)
                        + " Иначе тест assertWaterFish перестанет ловить эту поломку. При смене assertWaterFish проверь это утверждение.");
        assertTrue(
                barePlan.task("fish").isEmpty(),
                "Путь рыбалки требует воду в округе, а не выдумывает её: " + ids(barePlan)
                        + " Иначе тест assertWaterFish перестанет ловить эту поломку. При смене assertWaterFish проверь это утверждение.");
        GraphPlan.Task bareWait = task(barePlan, "event:hungry:wait_food");
        GraphPlan.Task bareMine = task(barePlan, "mine_coal");

        assertTrue(
                bareWait.feasible(),
                "Источника еды нет совсем — дух ждёт, а не копает: " + ids(barePlan)
                        + " Иначе тест assertWaterFish перестанет ловить эту поломку. При смене assertWaterFish проверь это утверждение.");
        assertTrue(
                bareWait.priority() > bareMine.priority(),
                "Ожидание еды выше шахты, когда источника нет: ожидание " + bareWait.priority() + ", шахта "
                        + bareMine.priority()
                        + " Иначе тест assertWaterFish перестанет ловить эту поломку. При смене assertWaterFish проверь это утверждение.");
        assertEquals(
                bareWait.id(),
                tickets(bare, facts).post().get(0).task().id(),
                "Первым на доске ожидание еды, а не копать: "
                        + tickets(bare, facts).post().stream()
                                .map(GraphTickets.Ticket::id)
                                .toList()
                        + " Иначе тест assertWaterFish перестанет ловить эту поломку. При смене assertWaterFish проверь это утверждение.");
        return bare;
    }

    private static void assertLiveSnapshot() {
        GraphWorld live = GraphMutations.liveStageOne().event("hungry").with("food", 0);
        GraphFacts liveFacts =
                facts(named(spirit("spirit-1", 20, 2), spirit("spirit-2"), spirit("spirit-3"), spirit("spirit-4")));
        GraphPlan livePlan = plan(live, liveFacts);
        GraphPlan.Task liveHunt = task(livePlan, "event:hungry:hunt");
        GraphPlan.Task liveMine = task(livePlan, "mine_coal");

        assertTrue(
                liveHunt.priority() > liveMine.priority(),
                "На живом снимке этапа 1 голодный дух тоже идёт за едой раньше шахты: охота " + liveHunt.priority()
                        + ", шахта " + liveMine.priority()
                        + " Иначе тест assertLiveSnapshot перестанет ловить эту поломку. При смене assertLiveSnapshot проверь это утверждение.");
        assertEquals(
                Optional.of("spirit-1"),
                liveHunt.spirit(),
                "На живом снимке еду добывает самый голодный дух: " + liveHunt
                        + " Иначе тест assertLiveSnapshot перестанет ловить эту поломку. При смене assertLiveSnapshot проверь это утверждение.");
    }

    private static void assertBareDispatch(
            GraphWorld bare, GraphFacts facts, List<DispatchRound.Candidate> candidates) {
        GraphPlan barePlan = plan(bare, facts);
        GraphPlan.Task bareWait = task(barePlan, "event:hungry:wait_food");
        GraphPlan.Task bareMine = task(barePlan, "mine_coal");
        GraphTickets.Result bareTickets = tickets(bare, facts);
        DispatchRound bareRound = new DispatchRound(board(bare, bareTickets), 0);
        assertEquals(
                "spirit-1",
                bareRound
                        .choose(
                                boardTicket(
                                        ticketFor(bareTickets, GraphTickets.id(bareWait)), Optional.of(id("spirit-1"))),
                                candidates)
                        .map(DispatchRound.Candidate::name)
                        .orElse(""),
                "Без источника еды ждёт голодный дух, а не сытый: " + candidates
                        + " Иначе тест assertBareDispatch перестанет ловить эту поломку. При смене assertBareDispatch проверь это утверждение.");
        bareRound.take(id("spirit-1"));
        assertEquals(
                "spirit-2",
                bareRound
                        .choose(boardTicket(ticketFor(bareTickets, GraphTickets.id(bareMine))), candidates)
                        .map(DispatchRound.Candidate::name)
                        .orElse(""),
                "Сытый дух копает, пока голодный ждёт еду: " + candidates
                        + " Иначе тест assertBareDispatch перестанет ловить эту поломку. При смене assertBareDispatch проверь это утверждение.");
    }

    @Test
    void hunger_FoodInChest_GateGivesEatNotMine() {
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
        int need = GraphPriorities.need(GraphWorld.GRAPH);

        assertTrue(
                eat.feasible(),
                "Еда в сундуке делает поесть выполнимым: " + ids(plan)
                        + " Иначе тест hunger_FoodInChest_GateGivesEatNotMine перестанет ловить эту поломку. При смене hunger_FoodInChest_GateGivesEatNotMine проверь это утверждение.");
        assertTrue(
                eat.raised() && eat.priority() <= need && eat.priority() >= need - VitalNeeds.BAND,
                "Обед голодного духа идёт в полосе потребности: " + eat.priority()
                        + " Иначе тест hunger_FoodInChest_GateGivesEatNotMine перестанет ловить эту поломку. При смене hunger_FoodInChest_GateGivesEatNotMine проверь это утверждение.");
        assertTrue(
                plan.task("event:hungry:hunt").isEmpty(),
                "Еда уже есть — охота не заказывается повторно: " + ids(plan)
                        + " Иначе тест hunger_FoodInChest_GateGivesEatNotMine перестанет ловить эту поломку. При смене hunger_FoodInChest_GateGivesEatNotMine проверь это утверждение.");
        assertTrue(
                plan.task("event:hungry:wait_food").isEmpty(),
                "Еда уже есть — ждать её незачем: " + ids(plan)
                        + " Иначе тест hunger_FoodInChest_GateGivesEatNotMine перестанет ловить эту поломку. При смене hunger_FoodInChest_GateGivesEatNotMine проверь это утверждение.");
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
                "Голодному духу не выдаётся шахта, пока есть путь к еде: "
                        + round.refusal(boardTicket(ticketFor(result, GraphTickets.id(mine))), candidates.get(0))
                        + " Иначе тест hunger_FoodInChest_GateGivesEatNotMine перестанет ловить эту поломку. При смене hunger_FoodInChest_GateGivesEatNotMine проверь это утверждение.");
        assertEquals(
                "spirit-1",
                round.choose(boardTicket(ticketFor(result, GraphTickets.id(eat))), candidates)
                        .map(DispatchRound.Candidate::name)
                        .orElse(""),
                "Поесть достаётся голодному духу, а не сытому"
                        + " Иначе тест hunger_FoodInChest_GateGivesEatNotMine перестанет ловить эту поломку. При смене hunger_FoodInChest_GateGivesEatNotMine проверь это утверждение.");
        assertEquals(
                BoardCase.EAT_TO_FULL,
                eat.boardCase(),
                "Еда — внешний кейс, дух ест сам, доска только отдаёт задание"
                        + " Иначе тест hunger_FoodInChest_GateGivesEatNotMine перестанет ловить эту поломку. При смене hunger_FoodInChest_GateGivesEatNotMine проверь это утверждение.");
    }

    private static int order(GraphPlan plan, String id) {
        return plan.tasks().stream().map(GraphPlan.Task::id).toList().indexOf(id);
    }
}
