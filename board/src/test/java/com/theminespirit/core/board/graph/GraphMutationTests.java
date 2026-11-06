package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class GraphMutationTests {
    private static List<String> sweep(BiConsumer<GraphWorld, List<String>> check) {
        List<String> found = new ArrayList<>();
        for (GraphWorld base : GraphMutations.baselines()) {
            for (GraphMutations.Mutation first : GraphMutations.single()) {
                GraphWorld world = first.apply().apply(base);
                List<String> local = new ArrayList<>();
                check.accept(world, local);
                local.forEach(entry -> found.add(first.name() + ": " + entry));
            }
        }
        return found;
    }

    private static List<String> head(List<String> found) {
        return found.subList(0, Math.min(5, found.size()));
    }

    private static void stalled(GraphWorld world, List<String> out) {
        GraphPlan plan = world.plan();
        if (!plan.tasks().isEmpty() && GraphWorld.issueOrder(plan).isEmpty()) {
            out.add("ни одного выполнимого из " + plan.tasks().size());
        }
    }

    @Tag("mutation")
    @Test
    void plan_EverySingle_NoStall() {
        List<String> found = sweep(GraphMutationTests::stalled);
        assertEquals(List.of(), head(found), "Полный перебор одиночных мутаций: мир встал (" + found.size() + ")");
    }

    @Tag("mutation")
    @Test
    void flow_LessStock_NeverLowersProducer() {
        List<String> found = new ArrayList<>();
        for (GraphWorld base : GraphMutations.baselines()) {
            for (SupplyGraph.Item item : GraphWorld.GRAPH.items().values()) {
                GraphWorld more = base.copy().with(item.id(), item.xmin() * 2);
                GraphWorld less = base.copy().with(item.id(), 0);
                Map<String, Double> moreFlow =
                        GraphPlanner.flow(more.graph, more.state()).solve();
                Map<String, Double> lessFlow =
                        GraphPlanner.flow(less.graph, less.state()).solve();
                for (SupplyGraph.Way way : GraphWorld.GRAPH.producers(item.id())) {
                    double plenty = moreFlow.getOrDefault(way.id(), 0.0);
                    double none = lessFlow.getOrDefault(way.id(), 0.0);
                    if (plenty > none + 1e-9) {
                        found.add(item.id() + "/" + way.id() + ": поток " + plenty + " при запасе > " + none
                                + " без запаса");
                    }
                }
            }
        }
        assertEquals(List.of(), head(found), "Меньше запаса — не ниже поток добычи (" + found.size() + ")");
    }

    @Tag("mutation")
    @Test
    void plan_SameWorld_Deterministic() {
        List<String> found = new ArrayList<>();
        Random random = new Random(MutationSeeds.seed());
        for (GraphWorld base : GraphMutations.baselines()) {
            for (int index = 0; index < 100; index++) {
                GraphWorld world = GraphMutations.sample(random.nextLong(), 1)
                        .get(0)
                        .apply()
                        .apply(base);
                if (!world.plan().equals(world.plan())) {
                    found.add("два расчёта разошлись");
                }
            }
        }
        assertEquals(List.of(), head(found), "Расчёт графа обязан быть детерминированным");
    }

    @Test
    void plan_MaterialsReady_BuildingOutranksStockTopUp() {
        GraphPlan plan = GraphWorld.settled()
                .with("oak_log", 64)
                .with("oak_planks", 64)
                .with("cobblestone", 64)
                .with("dirt", 0)
                .plan();

        assertTrue(
                plan.priority("building:hut") > plan.priority("dig_dirt"),
                "Материалы хижины на складе — стройка важнее пополнения запаса земли до порога: хижина "
                        + plan.priority("building:hut") + ", земля " + plan.priority("dig_dirt"));
    }

    @Test
    void plan_StageOneKitStone_IronChainNotPulled() {
        GraphPlan plan =
                GraphWorld.settled().tools("spirit-1", 0).with("oak_log", 0).plan();

        assertTrue(
                plan.task("mine_iron").isEmpty() && plan.task("smelt_iron").isEmpty(),
                "Набор этапа 1 — каменный: железный инструмент стоит заданием, но не тянет добычу и плавку железа: "
                        + plan.task("mine_iron") + ", " + plan.task("smelt_iron"));
    }

    @Test
    void issueOrder_EmptyWorld_ChopIsFeasible() {
        GraphPlan plan = GraphWorld.empty().built("crafting_table", Built.BUILT).plan();

        assertTrue(
                GraphWorld.issueOrder(plan).stream().anyMatch(task -> "chop_oak_log".equals(task.id())),
                "В пустом мире рубка выполнима сразу, руками: " + GraphWorld.issueOrder(plan));
    }

    @Tag("mutation")
    @Test
    void pairs_Sampled_NoStall() {
        List<String> found = new ArrayList<>();
        List<GraphWorld> bases = GraphMutations.baselines();
        List<GraphMutations.Mutation> all = GraphMutations.single();
        Random random = new Random(MutationSeeds.seed() ^ 0x9E3779B97F4A7C15L);
        for (int index = 0; index < 20_000; index++) {
            GraphWorld base = bases.get(random.nextInt(bases.size()));
            GraphMutations.Mutation first = all.get(random.nextInt(all.size()));
            GraphMutations.Mutation second = all.get(random.nextInt(all.size()));
            GraphWorld world = second.apply().apply(first.apply().apply(base));
            List<String> local = new ArrayList<>();
            stalled(world, local);
            local.forEach(entry -> found.add(first.name() + " + " + second.name() + ": " + entry));
        }
        assertEquals(List.of(), head(found), "Пары мутаций (выборка 20000): мир встал (" + found.size() + ")");
    }
}
