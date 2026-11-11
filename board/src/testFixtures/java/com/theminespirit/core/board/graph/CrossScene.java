package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.EatNeed;
import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CrossScene {
    static final GraphFacts.Position HOME = new GraphFacts.Position(0, 64, 0);
    static final BlockCoord STAND = new BlockCoord(0, 64, 0);
    static final EatNeed FED = new EatNeed(20, 20, 20, 20);
    static final EatNeed HUNGRY = new EatNeed(4, 20, 20, 20);
    private static final List<String> TIERS = List.of("wooden", "stone", "iron", "diamond", "netherite");

    private CrossScene() {}

    static GraphWorld stageOne() {
        GraphWorld world = GraphWorld.settled();
        for (String building : List.of("lighting", "fence", "hut", "streets", "orchard_oak")) {
            world.built(building, Built.BUILT);
        }
        return world;
    }

    static GraphWorld stageTwo() {
        GraphWorld world = stageOne();
        world.stage = 2;
        for (String building : List.of("warehouse", "smithing_table", "anvil", "plaza", "family_house_a", "field")) {
            world.built(building, Built.PLANNED);
        }
        return world;
    }

    static GraphFacts facts(
            Map<String, GraphFacts.Spirit> spirits,
            List<GraphFacts.Threat> threats,
            List<GraphFacts.Chest> chests,
            GraphSiteFacts.Terrain terrain,
            Map<String, GraphFacts.Building> buildings) {
        return new GraphFacts(
                spirits,
                threats,
                chests,
                List.of(),
                Map.of(),
                buildings,
                List.of(),
                terrain,
                new GraphSiteFacts.StageReqs(Map.of(), Map.of()),
                GraphFeedback.none(),
                List.of(),
                List.of(),
                List.of());
    }

    static GraphFacts facts(Map<String, GraphFacts.Spirit> spirits) {
        return facts(spirits, List.of(), List.of(), GraphSiteFacts.Terrain.empty(), Map.of());
    }

    static GraphFacts.Spirit spirit(String id) {
        return spirit(id, 20, 20, false, false, null, List.of(), List.of());
    }

    static GraphFacts.Spirit spirit(String id, float health, int food) {
        return spirit(id, health, food, false, false, null, List.of(), List.of());
    }

    static GraphFacts.Spirit spirit(
            String id,
            float health,
            int food,
            boolean trapped,
            boolean stuck,
            String trap,
            List<GraphFacts.Tool> tools,
            List<GraphFacts.Armor> armor) {
        return spirit(id, health, food, trapped, stuck, trap, null, Map.of(), tools, armor);
    }

    static GraphFacts.Spirit spirit(
            String id,
            float health,
            int food,
            boolean trapped,
            boolean stuck,
            String trap,
            String activity,
            Map<String, Integer> inventory,
            List<GraphFacts.Tool> tools,
            List<GraphFacts.Armor> armor) {
        return new GraphFacts.Spirit(
                id,
                HOME,
                health,
                Math.max(20, health),
                health < 20 ? new GraphFacts.Hurt(6, "minecraft:zombie", 5) : null,
                food,
                300,
                false,
                trapped,
                stuck,
                activity,
                inventory,
                armor,
                tools,
                trap);
    }

    static GraphFacts.Tool tool(String kind, int tier, int durability) {
        return tool(kind, tier, durability, Math.max(1, durability));
    }

    static GraphFacts.Tool tool(String kind, int tier, int durability, int maxDurability) {
        String tierName = TIERS.get(Math.max(0, Math.min(tier, TIERS.size() - 1)));
        return new GraphFacts.Tool("minecraft:" + tierName + "_" + kind, kind, tier, durability, maxDurability);
    }

    static GraphFacts.Armor armor(String slot, String item, int durability) {
        return new GraphFacts.Armor(slot, "minecraft:" + item, durability, durability);
    }

    static GraphFacts.Threat threat(String id, String target, GraphFacts.TargetKind kind, int distance) {
        return new GraphFacts.Threat(
                id,
                "minecraft:zombie",
                20,
                20,
                new GraphFacts.Position(distance, 64, 0),
                target,
                kind,
                kind == GraphFacts.TargetKind.SPIRIT,
                true,
                null,
                distance,
                null,
                0.2);
    }

    static GraphFacts.Chest chest(Map<String, Integer> contents) {
        return new GraphFacts.Chest(new GraphFacts.Position(1, 64, 1), contents, 10, 27, GraphFacts.Reach.UNKNOWN);
    }

    static GraphFacts.Building damaged(String id, int audit, int damage, Map<String, Integer> missing) {
        return new GraphFacts.Building(id, 1, audit, damage, missing, Map.of(), 0, null, false, true);
    }

    static GraphFacts.Building unknown(String id) {
        return new GraphFacts.Building(id, 0, 0, 0, Map.of(), Map.of(), 0, null, true, true);
    }

    static GraphPlan plan(GraphWorld world, GraphFacts facts) {
        return GraphPlanner.plan(
                world.graph, world.state().withFacts(facts, world.graph.costs().life()));
    }

    static GraphTickets.Result tickets(GraphWorld world, GraphFacts facts) {
        return GraphTickets.of(
                world.graph, world.state().withFacts(facts, world.graph.costs().life()), plan(world, facts));
    }

    static GraphTickets.Ticket ticketFor(GraphTickets.Result result, String id) {
        return result.post().stream()
                .filter(ticket -> ticket.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("На доске нет билета " + id + ": "
                        + result.post().stream().map(GraphTickets.Ticket::id).toList()));
    }

    static GraphPlan.Task task(GraphPlan plan, String id) {
        return plan.task(id)
                .orElseThrow(() -> new AssertionError("Нет задания " + id + ": "
                        + plan.tasks().stream().map(GraphPlan.Task::id).toList()));
    }

    static final String ORDER_HINT = "ПОРЯДОК: решение неадекватно — поправь веса графа; новый порядок разумен "
            + "(например, после новой механики) — поправь ожидание теста с причиной. Веса под тест не подгонять";

    static List<String> ahead(GraphPlan plan, GraphPlan.Task task) {
        return hinted(plan.tasks().stream()
                .filter(other -> other.priority() > task.priority())
                .map(other -> other.id() + "=" + other.priority() + " > " + task.id() + "=" + task.priority())
                .toList());
    }

    static List<String> workNotBehind(GraphPlan plan, GraphPlan.Task task) {
        return hinted(plan.tasks().stream()
                .filter(other -> other != task && !life(other) && other.priority() >= task.priority())
                .map(other -> other.id() + "=" + other.priority() + " >= " + task.id() + "=" + task.priority())
                .toList());
    }

    private static List<String> hinted(List<String> wrong) {
        if (wrong.isEmpty()) {
            return wrong;
        }
        List<String> out = new ArrayList<>();
        out.add(ORDER_HINT);
        out.addAll(wrong);
        return out;
    }

    static boolean life(GraphPlan.Task task) {
        return "rescue_spirit".equals(task.id()) || task.id().startsWith("event:life_");
    }

    static List<String> ids(GraphPlan plan) {
        return plan.tasks().stream().map(GraphPlan.Task::id).toList();
    }

    static List<String> tasksMaking(GraphPlan plan, String item) {
        return plan.tasks().stream()
                .filter(task -> item.equals(task.makes()))
                .map(GraphPlan.Task::id)
                .toList();
    }

    static List<String> tasksFor(GraphPlan plan, BoardCase boardCase) {
        return plan.tasks().stream()
                .filter(task -> task.boardCase() == boardCase)
                .map(GraphPlan.Task::id)
                .toList();
    }

    static Map<String, GraphFacts.Spirit> named(GraphFacts.Spirit... spirits) {
        Map<String, GraphFacts.Spirit> map = new LinkedHashMap<>();
        for (GraphFacts.Spirit spirit : spirits) {
            map.put(spirit.id(), spirit);
        }
        return map;
    }
}
