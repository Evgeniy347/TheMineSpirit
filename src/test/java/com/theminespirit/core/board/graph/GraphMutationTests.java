package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
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

    @Test
    void plan_AnyWorld_HasFeasibleTaskWhenWorkLeft() {
        List<String> found = sweep((world, out) -> {
            GraphPlan plan = world.plan();
            if (!plan.tasks().isEmpty() && GraphWorld.issueOrder(plan).isEmpty()) {
                out.add("ни одного выполнимого из " + plan.tasks().size());
            }
        });
        assertEquals(List.of(), head(found), "Мир встал: задания есть, а выполнить нечего (" + found.size() + ")");
    }

    @Test
    void plan_InfeasibleTask_EveryGapHasProducer() {
        List<String> found = sweep((world, out) -> {
            GraphPlan plan = world.plan();
            GraphState state = world.state();
            for (GraphPlan.Task task : plan.tasks()) {
                if (task.chance()) {
                    continue;
                }
                for (Map.Entry<String, Integer> input : task.consumes().entrySet()) {
                    if (state.count(input.getKey()) < input.getValue()
                            && plan.tasks().stream()
                                    .noneMatch(other -> other.makes().equals(input.getKey()))) {
                        out.add(task.id() + " ждёт " + input.getKey() + ", а его никто не добывает");
                    }
                }
            }
        });
        assertEquals(List.of(), head(found), "Тупик: нехватка без задания добычи (" + found.size() + ")");
    }

    @Test
    void plan_LessStock_NeverLowersProducer() {
        List<String> found = new ArrayList<>();
        for (GraphWorld base : GraphMutations.baselines()) {
            for (SupplyGraph.Item item : GraphWorld.GRAPH.items().values()) {
                GraphPlan more = base.copy().with(item.id(), item.xmin() * 2).plan();
                GraphPlan less = base.copy().with(item.id(), 0).plan();
                for (SupplyGraph.Way way : GraphWorld.GRAPH.producers(item.id())) {
                    if (more.priority(way.id()) > less.priority(way.id())) {
                        found.add(way.id() + ": " + more.priority(way.id()) + " при запасе > " + less.priority(way.id())
                                + " без запаса");
                    }
                }
            }
        }
        assertEquals(List.of(), head(found), "Меньше запаса — не ниже приоритет добычи (" + found.size() + ")");
    }

    @Test
    void plan_SameWorld_Deterministic() {
        List<String> found = sweep((world, out) -> {
            if (!world.plan().equals(world.plan())) {
                out.add("два расчёта разошлись");
            }
        });
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
                GraphWorld.issueOrder(plan).stream().anyMatch(task -> task.id().equals("chop_log")),
                "В пустом мире рубка выполнима сразу, руками: " + GraphWorld.issueOrder(plan));
    }
}
