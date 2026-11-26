package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
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
import java.util.Map;
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
                        + "иначе gear_craft_hoe остаётся без исполнителя, а пашню нечем делать"
                        + " При смене gatherMaterials_WithCraftItem_CraftsHoe проверь это утверждение.");
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
                "Кирка для шахты крафтится своим кейсом: задание без шага craft оставило бы духа без инструмента"
                        + " Иначе тест plan_CraftStonePickaxe_CraftsNamedItem перестанет ловить эту поломку. При смене plan_CraftStonePickaxe_CraftsNamedItem проверь это утверждение.");
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
                        + scripts(first)
                        + " При смене plan_PlacedWithLeveling_LevelsBeforeBuild проверь это утверждение.");
        assertTrue(
                scripts(plan).contains("build_schematic"),
                "После выравнивания грядка строится по схеме — " + scripts(plan)
                        + " Иначе тест plan_PlacedWithLeveling_LevelsBeforeBuild перестанет ловить эту поломку. При смене plan_PlacedWithLeveling_LevelsBeforeBuild проверь это утверждение.");
    }

    @Test
    void plan_PlacedWithoutLeveling_GoesToSchematicEntrance() {
        TaskNode plan = TicketPlans.plan(
                BoardCase.BUILD_PLACED,
                1,
                TicketTarget.at(new BlockCoord(10, 60, -5)).withScheme("wheat_field_1"));

        ScriptCall gotoCall = calls(plan, "goto").get(0);

        assertEquals(
                Map.of("x", 17, "y", 61, "z", -3),
                gotoCall.params(),
                "Перед стройкой дух идёт к входу схемы, а не к её углу: иначе wheat_field снова получает "
                        + "goto в рельеф и clear_area без прогресса. При смене plan_PlacedWithoutLeveling_GoesToSchematicEntrance "
                        + "проверь StagePlans.placed, Preset.entry и живой участок (-134,64,-467).");
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
