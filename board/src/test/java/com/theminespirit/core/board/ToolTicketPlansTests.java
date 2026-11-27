package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void plan_TilledWithLeveling_SkipsLevelingAndBuilds() {
        PlotLeveling leveling = new PlotLeveling(
                List.of(new PlotLeveling.Box(0, 65, 0, 6, 65, 11)), List.of(), PlotLeveling.DEFAULT_BLOCK);
        TaskNode plan = TicketPlans.plan(
                BoardCase.BUILD_PLACED,
                1,
                TicketTarget.at(new BlockCoord(0, 64, 0))
                        .withScheme("wheat_field_1")
                        .withLeveling(leveling));

        assertFalse(
                scripts(plan).contains("clear_area"),
                "Пашня не запускает LevelRepair: иначе готовая земля, вода и посевы сносятся перед "
                        + "повторной стройкой. При смене plan_TilledWithLeveling_SkipsLevelingAndBuilds "
                        + "проверь StagePlans.placed и живой wheat_field.");
        assertTrue(
                scripts(plan).contains("build_schematic"),
                "Пашня строится по схеме — " + scripts(plan)
                        + " Иначе тест plan_TilledWithLeveling_SkipsLevelingAndBuilds перестанет ловить эту поломку. "
                        + "При смене plan_TilledWithLeveling_SkipsLevelingAndBuilds проверь StagePlans.placed.");
    }

    @Test
    void plan_BuildingWithLeveling_LevelsBeforeBuild() {
        PlotLeveling leveling = new PlotLeveling(
                List.of(new PlotLeveling.Box(0, 65, 0, 6, 65, 11)), List.of(), PlotLeveling.DEFAULT_BLOCK);
        TaskNode plan = TicketPlans.plan(
                BoardCase.BUILD_PLACED,
                1,
                TicketTarget.at(new BlockCoord(0, 64, 0))
                        .withScheme("stone_house")
                        .withLeveling(leveling));

        assertTrue(
                scripts(plan).contains("clear_area") && scripts(plan).contains("build_schematic"),
                "Обычная постройка на неровной земле сначала выравнивается: иначе дом ставится в воздух. "
                        + "При смене plan_BuildingWithLeveling_LevelsBeforeBuild проверь StagePlans.placed.");
    }

    @Test
    void plan_TilledWithoutLeveling_SkipsEntranceGoto() {
        TaskNode plan = TicketPlans.plan(
                BoardCase.BUILD_PLACED,
                1,
                TicketTarget.at(new BlockCoord(10, 60, -5)).withScheme("wheat_field_1"));

        assertEquals(
                0,
                calls(plan, "goto").size(),
                "Пашня не делает лишний goto к входу: иначе уже готовое поле снова падает в рельефе "
                        + "до проверки схемы. При смене plan_TilledWithoutLeveling_SkipsEntranceGoto "
                        + "проверь StagePlans.placed и живой wheat_field.");
        assertTrue(
                scripts(plan).contains("build_schematic"),
                "После снабжения пашня сразу проверяется схемой: иначе без goto задание вообще "
                        + "не доходит до стройки. При смене plan_TilledWithoutLeveling_SkipsEntranceGoto "
                        + "проверь StagePlans.placed.");
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
