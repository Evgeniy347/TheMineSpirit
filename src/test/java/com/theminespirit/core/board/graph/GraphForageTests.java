package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GraphForageTests {
    private static final Map<String, String> FORAGE = forage();

    private static Map<String, String> forage() {
        Map<String, String> ways = new LinkedHashMap<>();
        ways.put("pick_blue_orchid", "flower:blue_orchid");
        ways.put("pick_lilac", "flower:lilac");
        ways.put("pick_peony", "flower:peony");
        ways.put("pick_sunflower", "flower:sunflower");
        ways.put("pick_lily_of_the_valley", "flower:lily_of_the_valley");
        ways.put("pick_orange_tulip", "flower:orange_tulip");
        ways.put("pick_pink_petals", "flower:pink_petals");
        ways.put("forage_brown_mushroom", "mushrooms");
        ways.put("forage_red_mushroom", "mushrooms");
        ways.put("harvest_bamboo", "bamboo_jungle");
        ways.put("harvest_kelp", "ocean");
        ways.put("shear_vine", "biome:jungle");
        ways.put("dig_mud", "mangrove_swamp");
        ways.put("chop_mangrove_roots", "mangrove_swamp");
        ways.put("loot_rabbit", "rabbits");
        ways.put("loot_rabbit_hide", "rabbits");
        ways.put("loot_glow_ink_sac", "glow_squid");
        ways.put("brush_armadillo_scute", "armadillo");
        return Map.copyOf(ways);
    }

    @Test
    void ways_ForageStageTwo_Implemented() {
        FORAGE.keySet()
                .forEach(way -> assertTrue(
                        GraphWorld.GRAPH.implemented(way),
                        "Сбор в природе реализован — с пути " + way + " снята пометка implemented=false"));
    }

    @Test
    void plan_ForageWithWorldTag_TaskFeasible() {
        FORAGE.forEach((way, tag) -> {
            GraphPlan plan = plan(way, tag);
            Optional<GraphPlan.Task> task = plan.task(way);
            assertTrue(
                    task.isPresent()
                            && task.orElseThrow().implemented()
                            && task.orElseThrow().feasible(),
                    "Метка мира " + tag + " рядом — путь " + way + " стоит на доске выполнимым: " + task);
        });
    }

    @Test
    void plan_ForageWithoutWorldTag_NotFeasible() {
        FORAGE.forEach((way, tag) -> {
            GraphPlan plan = plan(way, null);
            boolean feasible = plan.task(way).map(GraphPlan.Task::feasible).orElse(false);
            assertFalse(
                    feasible,
                    "Без метки мира " + tag + " путь " + way + " невыполним: иначе дух пойдёт искать ресурс, "
                            + "которого в округе нет");
        });
    }

    private static GraphPlan plan(String way, String tag) {
        GraphWorld world = GraphWorld.settled();
        world.stage = 2;
        world.stock.put("shears", 1);
        world.stock.put("brush", 1);
        if (tag != null) {
            world.world.add(tag);
        }
        return GraphPlanner.plan(GraphWorld.GRAPH, world.state().withOrders(Map.of(makes(way), 1)));
    }

    private static String makes(String way) {
        return GraphWorld.GRAPH.way(way).orElseThrow().makes();
    }
}
