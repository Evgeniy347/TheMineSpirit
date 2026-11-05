package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class TicketToolPlanTests {
    @Test
    void plan_GatherCoal_AcquiresPickaxeBeforeVein() {
        TaskNode plan = gather("coal", 4);
        List<ScriptCall> steps = calls(plan);

        assertTrue(
                index(
                                steps,
                                call -> "acquire".equals(call.script())
                                        && "*_pickaxe".equals(call.params().get("item")))
                        < index(steps, call -> "mine_vein".equals(call.script())),
                "Кирка берётся со склада до добычи жилы: без неё шахта ждёт инструмент, а задание падает: " + steps);
    }

    @Test
    void plan_GatherCobblestone_AcquiresPickaxeBeforeStone() {
        TaskNode plan = gather("cobblestone", 8);
        List<ScriptCall> steps = calls(plan);

        assertTrue(
                index(
                                steps,
                                call -> "acquire".equals(call.script())
                                        && "*_pickaxe".equals(call.params().get("item")))
                        < index(
                                steps,
                                call -> "gather".equals(call.script())
                                        && "stone".equals(call.params().get("block"))),
                "Кирка берётся со склада до сбора камня: руками булыжник не добыть: " + steps);
    }

    @Test
    void plan_ClearGrounds_AcquiresShovelBeforeClear() {
        PlotLeveling leveling = new PlotLeveling(List.of(new PlotLeveling.Box(0, 64, 0, 0, 65, 0)), List.of(), "dirt");
        TaskNode plan = TicketPlans.plan(
                BoardCase.CLEAR_GROUNDS,
                TicketTarget.at(new BlockCoord(0, 64, 0)).withLeveling(leveling));
        List<ScriptCall> steps = calls(plan);

        assertTrue(
                index(
                                steps,
                                call -> "acquire".equals(call.script())
                                        && "*_shovel".equals(call.params().get("item")))
                        < index(steps, call -> "clear_area".equals(call.script())),
                "Лопата берётся со склада до расчистки: иначе дёрн и земля снимаются руками и работа не кончается: "
                        + steps);
    }

    @Test
    void plan_MineLeftOre_AcquiresPickaxeBeforeVein() {
        TaskNode plan = TicketPlans.plan(
                BoardCase.MINE_LEFT_ORE,
                TicketTarget.at(new BlockCoord(0, 60, 0)).withSupply("coal", 1));
        List<ScriptCall> steps = calls(plan);

        assertTrue(
                index(
                                steps,
                                call -> "acquire".equals(call.script())
                                        && "*_pickaxe".equals(call.params().get("item")))
                        < index(steps, call -> "mine_vein".equals(call.script())),
                "Оставленную руду тоже берут киркой со склада, а не голыми руками: " + steps);
    }

    private static TaskNode gather(String item, int count) {
        return TicketPlans.plan(
                BoardCase.GATHER_MATERIALS,
                TicketTarget.at(new BlockCoord(10, 60, -5)).withSupply(item, count));
    }

    private static int index(List<ScriptCall> steps, Predicate<ScriptCall> match) {
        for (int index = 0; index < steps.size(); index++) {
            if (match.test(steps.get(index))) {
                return index;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static List<ScriptCall> calls(TaskNode root) {
        List<ScriptCall> found = new ArrayList<>();
        collect(root, found);
        return found;
    }

    private static void collect(TaskNode node, List<ScriptCall> found) {
        if (node instanceof ScriptCall call) {
            found.add(call);
        } else if (node instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, found));
        } else if (node instanceof FallbackNode fallback) {
            fallback.steps().forEach(step -> collect(step, found));
        }
    }
}
