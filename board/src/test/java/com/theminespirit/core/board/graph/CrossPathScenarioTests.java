package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossActions.*;
import static com.theminespirit.core.board.graph.CrossScene.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.SpiritView;
import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CrossPathScenarioTests {
    private static final BlockCoord PIT = new BlockCoord(2, 60, 2);

    @Test
    void path_NoPathMark_RefusesEveryGraphTaskButRescue() {
        GraphWorld world = richWorld();
        GraphFacts facts = richFacts(List.of(threat("mob-a", "spirit-1", GraphFacts.TargetKind.SPIRIT, 1)));
        GraphPlan plan = plan(world, facts);
        GraphTickets.Result result = tickets(world, facts);
        Board board = board(world, result);
        board.stuck().see(id("spirit-2"), PIT, 0);
        board.stuck().mark(id("spirit-2"), 0, "пути нет: маршрут не найден");
        DispatchRound round = new DispatchRound(board, 0);
        DispatchRound.Candidate stuck = new DispatchRound.Candidate(
                id("spirit-2"), "spirit-2", null, List.of(), FED, SpiritView.free(), false, List.of());

        Map<String, String> work = new LinkedHashMap<>();
        work.put("chop_jungle_log", "1-3 рубка");
        work.put("mine_coal", "1-4 шахта");
        work.put("event:more6_carry_materials:more6_deposit_materials", "1-8 склад");
        work.put("event:gear_craft_pickaxe:craft_tool:pickaxe", "1-9 крафт");
        work.put("gather_seeds", "1-10 ферма");
        work.put("event:life_threat:retreat", "1-13 бегство");
        work.put("event:hungry:eat", "1-14 еда");
        work.put("event:life_threat:sleep", "1-15 сон");
        work.put("event:tool_worn:repair_tool", "1-16 ремонт инструмента");
        work.put("event:more5_mine_dark:more5_light_mine", "1-18 свет");
        work.put("brew_regeneration", "1-19 алхимия");
        work.put("event:expedition_ready:expedition", "1-22 поход");
        int refused = 0;
        for (Map.Entry<String, String> entry : work.entrySet()) {
            GraphPlan.Task task = task(plan, entry.getKey());
            assertTrue(task.feasible(), entry.getValue() + " должна быть выполнимой работой: " + ids(plan));
            GraphTickets.Ticket ticket = ticketFor(result, GraphTickets.id(task));
            Optional<String> why = round.refusal(boardTicket(ticket), stuck);
            assertTrue(why.isPresent(), entry.getValue() + " не достаётся духу без пути: " + ticket.id() + " — " + why);
            assertTrue(
                    why.orElseThrow().contains("пути нет"),
                    entry.getValue() + " отказ называет отсутствие пути, а не молчит: " + why);
            refused++;
        }
        assertEquals(12, refused, "Метка noPath проверяется на всех узлах работы, а не на одном");
        assertTrue(
                plan.task("rescue_spirit").filter(GraphPlan.Task::feasible).isPresent(),
                "Застрявший дух не остаётся без спасения: граф ставит rescue: " + ids(plan));
        assertTrue(
                result.post().stream().anyMatch(ticket -> ticket.id().startsWith("graph:rescue_spirit")),
                "Спасение стоит на доске рядом с работой: "
                        + result.post().stream().map(GraphTickets.Ticket::id).toList());
    }

    @Test
    void path_UnknownFieldAndTerrain_GraphWaitsInsteadOfGuessing() {
        GraphWorld world = stageTwo();
        world.with("wheat_seeds", 8)
                .with("dirt", 64)
                .with("oak_log", 64)
                .with("oak_planks", 64)
                .with("cobblestone", 64);
        world.event("field_empty");
        GraphFacts facts = new GraphFacts(
                named(spirit("spirit-1"), spirit("spirit-2")),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of("field", unknown("field"), "hut", unknown("hut")),
                List.of(),
                new GraphSiteFacts.Terrain(2, 0, 0, 2, 0, Map.of(), Map.of("minecraft:dirt", 4), Map.of(), true),
                new GraphSiteFacts.StageReqs(Map.of(), Map.of()),
                GraphFeedback.none(),
                List.of(),
                List.of());
        GraphPlan plan = plan(world, facts);

        assertFalse(
                task(plan, "event:field_empty:plant_crop").feasible(),
                "Поле в незагруженном чанке не сеется вслепую: граф держит посев невыполнимым: " + ids(plan));
        assertTrue(
                task(plan, "event:backfill:backfill").feasible(),
                "Рельеф помечен неизвестным, но граф всё равно ставит засыпку ямы: " + ids(plan));
        assertTrue(
                plan.task("building:hut").filter(GraphPlan.Task::feasible).isEmpty(),
                "Неизвестный дом не перестраивается вслепую: " + ids(plan));
        assertFalse(
                GraphStageGate.lotsReady(facts), "Неизвестный рельеф не считается ровным участком: этап ждёт аудита");
        assertTrue(
                plan.task("mine_coal").filter(GraphPlan.Task::feasible).isPresent(),
                "Остальная работа не пропадает из-за неизвестного участка: " + ids(plan));
    }

    private static GraphWorld richWorld() {
        GraphWorld world = stageTwo();
        world.stage = 3;
        world.built("brewing_stand", Built.BUILT)
                .built("anvil", Built.BUILT)
                .with("glass_bottle", 8)
                .with("nether_wart", 8)
                .with("ghast_tear", 2)
                .with("blaze_powder", 8)
                .with("iron_ingot", 8)
                .with("wheat_seeds", 8)
                .with("torch", 8)
                .with("coal", 8)
                .with("food", 8);
        world.event("night")
                .event("hungry")
                .event("tool_worn")
                .event("field_empty")
                .event("expedition_ready")
                .event("more5_mine_dark")
                .event("animal_starving")
                .event("more6_carry_materials");
        return world;
    }

    private static GraphFacts richFacts(List<GraphFacts.Threat> threats) {
        return facts(
                named(
                        spirit("spirit-1", 4, 20),
                        spirit("spirit-2", 20, 20, true, true, null, List.of(tool("pickaxe", 1, 1, 100)), List.of()),
                        spirit("spirit-3")),
                threats,
                List.of(chest(Map.of("food", 8, "oak_log", 8))),
                new GraphSiteFacts.Terrain(
                        2, 6, 0, 2, 1, Map.of(), Map.of("minecraft:dirt", 4), Map.of("minecraft:oak_leaves", 6)),
                Map.of(
                        "hut", damaged("hut", 20, 0, Map.of("oak_log", 20)),
                        "fence", damaged("fence", 40, 3, Map.of("oak_fence", 5))));
    }
}
