package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.town.PlotLeveling;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolTicketPlansTests {

    @Test
    void gatherMaterials_WithCraftItem_CraftsHoe() {
        TaskNode plan = TicketPlans.plan(
                BoardCase.GATHER_MATERIALS,
                TicketTarget.at(new BlockCoord(0, 64, 0))
                        .withCraft("minecraft:wooden_hoe", 1)
                        .withCount(1));

        assertTrue(
                calls(plan, "craft").stream()
                        .anyMatch(call ->
                                "minecraft:wooden_hoe".equals(call.params().get("item"))),
                "Тяпка не входит в набор инструментов доски: её крафт идёт общим путём с целью craft, "
                        + "иначе gear_craft_hoe остаётся без исполнителя, а пашню нечем делать");
    }

    @Test
    void plan_CraftStonePickaxe_CraftsNamedItem() {
        TaskNode plan = TicketPlans.plan(
                BoardCase.CRAFT_STONE_PICKAXE,
                TicketTarget.at(new BlockCoord(0, 64, 0)).withToolCount(1));

        assertTrue(
                calls(plan, "craft").stream()
                        .anyMatch(call ->
                                "minecraft:stone_pickaxe".equals(call.params().get("item"))),
                "Кирка для шахты крафтится своим кейсом: задание без шага craft оставило бы духа без инструмента");
    }

    @Test
    void plan_PlacedWithLeveling_LevelsBeforeBuild() {
        PlotLeveling leveling = new PlotLeveling(
                List.of(new PlotLeveling.Box(0, 65, 0, 6, 65, 11)), List.of(), PlotLeveling.DEFAULT_BLOCK);
        TaskNode plan = TicketPlans.plan(
                BoardCase.BUILD_PLACED,
                1,
                TicketTarget.at(new BlockCoord(0, 64, 0))
                        .withScheme("wheat_field_1")
                        .withLeveling(leveling));

        SequenceNode root = (SequenceNode) plan;
        TaskNode first = root.steps().get(0);
        assertTrue(
                first instanceof SequenceNode && scripts(first).contains("clear_area"),
                "Грядка на неровной земле сначала выравнивается: иначе пашня ставится в воздух/камень — "
                        + scripts(first));
        assertTrue(
                scripts(plan).contains("build_schematic"),
                "После выравнивания грядка строится по схеме — " + scripts(plan));
    }

    private static List<ScriptCall> calls(TaskNode root, String script) {
        List<ScriptCall> found = new ArrayList<>();
        for (ScriptCall call : calls(root)) {
            if (call.script().equals(script)) {
                found.add(call);
            }
        }
        return found;
    }

    private static List<ScriptCall> calls(TaskNode node) {
        List<ScriptCall> found = new ArrayList<>();
        switch (node) {
            case ScriptCall call -> found.add(call);
            case SequenceNode sequence -> sequence.steps().forEach(step -> found.addAll(calls(step)));
            case RepeatNode repeat -> found.addAll(calls(repeat.step()));
            case FallbackNode fallback -> fallback.steps().forEach(step -> found.addAll(calls(step)));
            case ParallelNode parallel -> parallel.steps().forEach(step -> found.addAll(calls(step)));
            case ConditionNode condition -> {
                found.addAll(calls(condition.thenStep()));
                if (condition.elseStep() != null) {
                    found.addAll(calls(condition.elseStep()));
                }
            }
        }
        return found;
    }

    private static List<String> scripts(TaskNode node) {
        return calls(node).stream().map(ScriptCall::script).toList();
    }
}
