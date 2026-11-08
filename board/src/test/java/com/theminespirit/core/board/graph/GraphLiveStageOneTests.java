package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphLiveStageOneTests {
    private static final int LIMIT = 4000;

    @Test
    void simulate_LiveStageOne_ClosesWholeStageOne() {
        GraphWorld world = GraphMutations.liveStageOne();
        Map<String, GraphPlan.Task> start = new HashMap<>();
        world.plan().tasks().forEach(task -> start.put(task.id(), task));
        assertTrue(start.containsKey("building:mine"), "Живой этап 1 обязан планировать шахту: " + start.keySet());
        assertFalse(
                start.get("building:mine").feasible(), "Шахта без кирки не выполняется, пока инструмент не скрафчен");
        assertTrue(world.world.contains(GraphNames.SOIL), "Живой этап 1 берёт землю с карьера: нет soil");
        assertTrue(
                start.containsKey("dig_dirt") && start.get("dig_dirt").feasible(),
                "Земля обязана быть выполнимой с карьера: " + start.get("dig_dirt"));

        GraphSimulation.Outcome outcome = GraphSimulation.runUntil(world, LIMIT, 2);
        assertEquals("", outcome.stuck(), "Живой этап 1 не закрылся: " + outcome.stuck());
        assertEquals(2, outcome.reachedStage(), "Живой этап 1 не дошёл до этапа 2");

        List<String> steps = new ArrayList<>();
        outcome.trail().stream().filter(step -> step.stage() == 1).forEach(step -> steps.add(step.task()));
        for (String task :
                List.of("building:mine", "building:streets", "building:orchard_oak", "building:wheat_field")) {
            assertTrue(steps.contains(task), "Этап 1 закрыт без '" + task + "': " + steps);
        }
        assertTrue(
                steps.stream().anyMatch(task -> task.contains("pickaxe")),
                "Шахта построена без крафта кирки: " + steps);
        assertTrue(steps.indexOf("building:mine") > firstPickaxe(steps), "Кирка должна появиться до шахты: " + steps);
        assertEquals(
                2, count(steps, "rescue_spirit"), "Два застрявших духа дают ровно два спасения, а не цикл: " + steps);
        assertTrue(count(steps, "dig_dirt") >= 8, "Карьер даёт землю улицам: " + steps);
        assertTrue(
                steps.stream().anyMatch(task -> task.startsWith("event:demolish_stray")),
                "Этап 1 обходит лишние постройки: " + steps);
        assertTrue(
                steps.stream().anyMatch(task -> task.startsWith("event:clear_floating")),
                "Этап 1 обходит висящее: " + steps);
        assertTrue(steps.size() <= 200, "Этап 1 зациклился: " + steps.size() + " заданий");
        assertTrue(maxRepeat(steps) <= 60, "Одно задание повторяется без продвижения: " + maxRepeat(steps));
        assertTrue(
                outcome.ticks() <= 10 * 60 * 1200,
                String.format(
                        "Поселение из снимка с четырьмя духами не уложилось в 10 минут игрового времени: %d тиков "
                                + "на %d заданий",
                        outcome.ticks(), steps.size()));
        assertTrue(
                outcome.ticks() > 0,
                "Симуляция закрыла этап за нуль тиков: время работ не учитывается, аудит мира бессмыслен");
    }

    private static int firstPickaxe(List<String> steps) {
        for (int index = 0; index < steps.size(); index++) {
            if (steps.get(index).contains("pickaxe")) {
                return index;
            }
        }
        return -1;
    }

    private static int count(List<String> steps, String id) {
        return (int) steps.stream().filter(id::equals).count();
    }

    private static int maxRepeat(List<String> steps) {
        Map<String, Integer> counts = new HashMap<>();
        int max = 0;
        for (String step : steps) {
            int value = counts.merge(step, 1, Integer::sum);
            max = Math.max(max, value);
        }
        return max;
    }

    @Test
    void world_Freed_LiftsTrap() {
        GraphWorld world = GraphWorld.empty();
        world.facts = GraphFacts.empty()
                .withSpirits(Map.of(
                        "spirit-1",
                        new GraphFacts.Spirit(
                                "spirit-1",
                                new GraphFacts.Position(0, 64, 0),
                                20.0f,
                                Math.max(20.0f, 1f),
                                null,
                                20,
                                300,
                                false,
                                true,
                                false,
                                null,
                                Map.of(),
                                List.of(),
                                List.of(),
                                "pit")));
        world.freed();
        assertFalse(world.facts.spirits().get("spirit-1").trapped(), "Выход из ловушки освобождает замурованного духа");
    }
}
