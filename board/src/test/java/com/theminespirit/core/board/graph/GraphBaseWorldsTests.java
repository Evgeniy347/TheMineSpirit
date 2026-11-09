package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GraphBaseWorldsTests {
    private static final int LIMIT = 4000;

    @Test
    void dirty_StraysAndDamage_ProduceCleanupAndRepair() {
        GraphWorld world = GraphBaseWorlds.dirty();
        GraphPlan plan = world.plan();
        for (String node : List.of(
                "event:demolish_stray:demolish_stray",
                "event:backfill:backfill",
                "event:clear_floating:clear_floating",
                "event:level_lot:level_lot",
                "event:repair_crafting_table:repair:crafting_table")) {
            assertTrue(
                    plan.tasks().stream().anyMatch(task -> task.id().equals(node)),
                    "Грязный участок обязан звать уборку и ремонт: нет " + node + " в " + ids(plan));
        }

        GraphSimulation.Outcome outcome = GraphSimulation.run(world, LIMIT);
        assertEquals("", outcome.stuck(), "Грязный участок не закрывается: " + outcome.stuck());
        List<String> steps =
                outcome.trail().stream().map(GraphSimulation.Step::task).toList();
        assertTrue(
                steps.stream().anyMatch(task -> task.startsWith("event:demolish_stray")),
                "Уборка лишних построек не попала в путь: " + steps.subList(0, Math.min(20, steps.size())));
        assertTrue(
                steps.stream().anyMatch(task -> task.startsWith("event:backfill")),
                "Ямы не засыпаны в пути: " + steps.subList(0, Math.min(20, steps.size())));
    }

    @Test
    void failures_MemoryBackedOff_KeepsWorkAvailable() {
        GraphWorld world = GraphBaseWorlds.failures();
        GraphPlan plan = world.plan();
        GraphPlan.Task wood = plan.tasks().stream()
                .filter(task -> "chop_oak_log".equals(task.id()))
                .findFirst()
                .orElse(null);
        assertTrue(wood != null, "Провал не убирает путь из плана: " + ids(plan));

        GraphSimulation.Outcome outcome = GraphSimulation.runUntil(world, LIMIT, 2);
        assertEquals("", outcome.stuck(), "Мир провалов не закрывает этап 1: " + outcome.stuck());
        assertEquals(2, outcome.reachedStage(), "Мир провалов не дошёл до этапа 2");
    }

    @Test
    void nightThreat_GuardsAndProtects_WithoutFarGather() {
        GraphWorld world = GraphBaseWorlds.nightThreat();
        GraphPlan plan = world.plan();
        for (String node : List.of(
                "event:life_threat:defend",
                "event:life_threat_spirit:protect_spirit",
                "event:expedition_ready:expedition")) {
            assertTrue(
                    plan.tasks().stream().anyMatch(task -> task.id().equals(node) && task.feasible()),
                    "Ночь с угрозой обязана выдавать оборону и поход: нет " + node + " в " + ids(plan));
        }
        assertFalse(
                plan.tasks().stream()
                        .anyMatch(task ->
                                "oak_log".equals(task.makes()) && task.id().startsWith("chop_") && task.feasible()),
                "Ночью далёкая рубка не выполнима: иначе сторож ночи провалит задачу на подходе");

        GraphSimulation.Outcome outcome = GraphSimulation.runUntil(world, LIMIT, 2);
        assertEquals("", outcome.stuck(), "Ночь с угрозой не закрывает этап 1: " + outcome.stuck());
        assertEquals(2, outcome.reachedStage(), "Ночь с угрозой не дошла до этапа 2");
        List<String> steps =
                outcome.trail().stream().map(GraphSimulation.Step::task).toList();
        assertTrue(
                steps.stream().anyMatch(task -> task.startsWith("event:life_threat")),
                "Угроза не снята защитой: " + steps.subList(0, Math.min(20, steps.size())));
    }

    @Test
    void noAxe_EmptyStart_ChopsByHandBeforeTool() {
        GraphWorld world = GraphBaseWorlds.emptyNoAxe();
        GraphPlan plan = world.plan();
        assertTrue(
                plan.tasks().stream().anyMatch(task -> "oak_log".equals(task.makes()) && task.feasible()),
                "Без топора дерево всё равно добывается руками: " + ids(plan));

        GraphSimulation.Outcome outcome = GraphSimulation.run(world, LIMIT);
        List<String> steps = new ArrayList<>();
        outcome.trail().stream().map(GraphSimulation.Step::task).forEach(steps::add);
        int firstWood = first(steps, "chop_jungle_log", "chop_oak_log");
        int firstAxe = first(steps, "craft_wooden_axe@", "craft_stone_axe@");
        assertTrue(firstWood >= 0, "Пустой старт не рубит дерево руками: " + steps);
        assertTrue(
                firstAxe < 0 || firstWood < firstAxe,
                "Топор не должен ждать себя: первая рубка " + firstWood + ", топор " + firstAxe);
        assertTrue(
                outcome.finished(),
                "Пустой старт без топора обязан дойти до последнего этапа, а не зациклиться: " + outcome.steps()
                        + " заданий, " + outcome.stuck());
        assertTrue(
                outcome.steps() <= LIMIT, "Пустой старт без топора не уложился в предел решений: " + outcome.steps());
        assertTrue(
                steps.stream().anyMatch(task -> task.contains("pickaxe")),
                "Пустой старт всё равно доходит до инструмента: " + steps);
    }

    private static String ids(GraphPlan plan) {
        return plan.tasks().stream().map(GraphPlan.Task::id).toList().toString();
    }

    private static int first(List<String> steps, String... ids) {
        for (int index = 0; index < steps.size(); index++) {
            for (String id : ids) {
                if (steps.get(index).startsWith(id)) {
                    return index;
                }
            }
        }
        return -1;
    }
}
