package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.town.PlotLeveling;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteTicketPlansTests {
    private static final BlockCoord HOME = new BlockCoord(10, 64, 10);
    private static final BlockCoord DIG = new BlockCoord(40, 64, 40);

    @Test
    void buildStreets_RoutePath_TakesBlocksClearsAndFills() {
        PlotLeveling plan = new PlotLeveling(
                List.of(new PlotLeveling.Box(0, 65, 0, 0, 65, 0)),
                List.of(new PlotLeveling.Box(0, 64, 0, 0, 64, 0)),
                "dirt");
        TicketTarget target = TicketTarget.at(new BlockCoord(0, 64, 0))
                .withRelocate(HOME, new BlockCoord(0, 64, 0))
                .withLeveling(plan);

        TaskNode task = TicketPlans.plan(BoardCase.BUILD_STREETS, target);

        assertTrue(!calls(task, "fill").isEmpty(), "Тропа кладёт блок дороги в подготовленную клетку: " + task);
        assertTrue(!calls(task, "clear_area").isEmpty(), "Сначала снимают верхний слой и траву, потом кладут дорогу");
        assertEquals(
                "minecraft:dirt",
                calls(task, "acquire").get(0).params().get("item"),
                "Материал дороги берётся из плана: без запаса fill нечего ставить");
    }

    @Test
    void gatherMaterials_RouteDig_ClearsLayersThenDepositsSand() {
        PlotLeveling plan = new PlotLeveling(List.of(new PlotLeveling.Box(40, 65, 40, 40, 66, 40)), List.of(), "dirt");
        TicketTarget target =
                TicketTarget.at(HOME).withRelocate(DIG, HOME).withLeveling(plan).withSupply("sand", 8);

        TaskNode task = TicketPlans.plan(BoardCase.GATHER_MATERIALS, target);

        List<ScriptCall> clears = calls(task, "clear_area");
        assertEquals(2, clears.size(), "Верхний и нижний слой снимаются по слою: " + task);
        assertEquals(66, clears.get(0).params().get("y1"), "Первым идёт верхний слой");
        assertTrue(
                !calls(task, "collect_drops").isEmpty(), "Дроп добычи собирают на месте: иначе песок останется в яме");
        ScriptCall deposit = calls(task, "deposit").get(0);
        assertEquals("sand", deposit.params().get("item"), "Дух несёт песок на склад: " + deposit);
        assertEquals(8, deposit.params().get("count"), "Несёт не больше задания и не меньше одного");
    }

    private static List<ScriptCall> calls(TaskNode task, String script) {
        List<ScriptCall> found = new ArrayList<>();
        collect(task, script, found);
        return found;
    }

    private static void collect(TaskNode node, String script, List<ScriptCall> found) {
        if (node instanceof ScriptCall call) {
            if (script.equals(call.script())) {
                found.add(call);
            }
            return;
        }
        if (node instanceof SequenceNode sequence) {
            sequence.steps().forEach(child -> collect(child, script, found));
        } else if (node instanceof FallbackNode fallback) {
            fallback.steps().forEach(child -> collect(child, script, found));
        }
    }
}
