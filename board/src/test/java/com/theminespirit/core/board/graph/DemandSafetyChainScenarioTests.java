package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossScene.facts;
import static com.theminespirit.core.board.graph.CrossScene.named;
import static com.theminespirit.core.board.graph.CrossScene.plan;
import static com.theminespirit.core.board.graph.CrossScene.spirit;
import static com.theminespirit.core.board.graph.CrossScene.stageTwo;
import static com.theminespirit.core.board.graph.CrossScene.task;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DemandSafetyChainScenarioTests {
    private static final Map<String, GraphFacts.Spirit> TWO = named(spirit("spirit-1"), spirit("spirit-2"));

    @Test
    void case14_RaidPerimeterDark_LightingAndTorchesRaised() {
        GraphWorld world = pair(stageTwo()).event("raid").built("lighting", Built.PLANNED);
        world.with("torch", 0).with("coal", 0).with("stick", 0);
        GraphPlan plan = plan(world, facts(TWO));
        GraphPlan.Task light = task(plan, "building:lighting");
        assertFalse(
                light.feasible(),
                "Света нет — постройка света ждёт факелы:  Иначе тест case14_RaidPerimeterDark_LightingAndTorchesRaised перестанет ловить эту поломку. При смене case14_RaidPerimeterDark_LightingAndTorchesRaised проверь это утверждение."
                        + ids(plan));
        assertTrue(
                task(plan, "craft_torch").raised(),
                "Набег поднимает факелы:  Иначе тест case14_RaidPerimeterDark_LightingAndTorchesRaised перестанет ловить эту поломку. При смене case14_RaidPerimeterDark_LightingAndTorchesRaised проверь это утверждение."
                        + ids(plan));
        assertTrue(
                task(plan, "mine_coal").raised(),
                "Угля нет — поднимается шахта угля:  Иначе тест case14_RaidPerimeterDark_LightingAndTorchesRaised перестанет ловить эту поломку. При смене case14_RaidPerimeterDark_LightingAndTorchesRaised проверь это утверждение."
                        + ids(plan));
        world.built("lighting", Built.BUILT).with("torch", 256).with("coal", 128);
        GraphPlan stocked = plan(world, facts(TWO));
        assertTrue(
                stocked.task("building:lighting").isEmpty(),
                "Свет построен — задание снято:  Иначе тест case14_RaidPerimeterDark_LightingAndTorchesRaised перестанет ловить эту поломку. При смене case14_RaidPerimeterDark_LightingAndTorchesRaised проверь это утверждение."
                        + ids(stocked));
        assertTrue(
                stocked.task("craft_torch").isEmpty(),
                "Факелов хватает — крафт снят:  Иначе тест case14_RaidPerimeterDark_LightingAndTorchesRaised перестанет ловить эту поломку. При смене case14_RaidPerimeterDark_LightingAndTorchesRaised проверь это утверждение."
                        + ids(stocked));
    }

    @Test
    void case15_TrappedInWater_RescueBlocksRaisedAndBridgeWaitsForPlanks() {
        GraphWorld world = pair(stageTwo());
        world.with("oak_log", 0).with("oak_planks", 0).with("cobblestone", 0);
        GraphFacts facts = facts(named(
                spirit(new CrossScene.SpiritSpec("spirit-1", 20, 20, true, false, "water", List.of(), List.of())),
                spirit("spirit-2")));
        GraphPlan plan = plan(world, facts);
        GraphPlan.Task bridge = task(plan, "bridge");
        assertFalse(
                bridge.feasible(),
                "Мост без досок ждёт материал:  Иначе тест case15_TrappedInWater_RescueBlocksRaisedAndBridgeWaitsForPlanks перестанет ловить эту поломку. При смене case15_TrappedInWater_RescueBlocksRaisedAndBridgeWaitsForPlanks проверь это утверждение."
                        + ids(plan));
        assertTrue(
                task(plan, "chop_oak_log").raised(),
                "Досок нет — поднимается рубка леса:  Иначе тест case15_TrappedInWater_RescueBlocksRaisedAndBridgeWaitsForPlanks перестанет ловить эту поломку. При смене case15_TrappedInWater_RescueBlocksRaisedAndBridgeWaitsForPlanks проверь это утверждение."
                        + ids(plan));
        assertTrue(
                task(plan, "mine_cobble").raised(),
                "Булыжника нет — поднимается шахта камня:  Иначе тест case15_TrappedInWater_RescueBlocksRaisedAndBridgeWaitsForPlanks перестанет ловить эту поломку. При смене case15_TrappedInWater_RescueBlocksRaisedAndBridgeWaitsForPlanks проверь это утверждение."
                        + ids(plan));
        world.with("oak_log", 160)
                .with("oak_planks", 128)
                .with("cobblestone", 160)
                .with("trail_clean", 1);
        GraphPlan stocked = plan(world, facts);
        assertTrue(
                task(stocked, "bridge").feasible(),
                "Доски и тропа есть — мост выполним:  Иначе тест case15_TrappedInWater_RescueBlocksRaisedAndBridgeWaitsForPlanks перестанет ловить эту поломку. При смене case15_TrappedInWater_RescueBlocksRaisedAndBridgeWaitsForPlanks проверь это утверждение."
                        + ids(stocked));
        assertTrue(
                stocked.task("chop_oak_log").isEmpty(),
                "Леса хватает — рубка снята:  Иначе тест case15_TrappedInWater_RescueBlocksRaisedAndBridgeWaitsForPlanks перестанет ловить эту поломку. При смене case15_TrappedInWater_RescueBlocksRaisedAndBridgeWaitsForPlanks проверь это утверждение."
                        + ids(stocked));
        assertTrue(
                stocked.task("mine_cobble").isEmpty(),
                "Камня хватает — шахта снята:  Иначе тест case15_TrappedInWater_RescueBlocksRaisedAndBridgeWaitsForPlanks перестанет ловить эту поломку. При смене case15_TrappedInWater_RescueBlocksRaisedAndBridgeWaitsForPlanks проверь это утверждение."
                        + ids(stocked));
    }

    @Test
    void case30_LavaNearMine_BlockingAndStoneRaisedBeforeWork() {
        GraphWorld world = pair(stageTwo()).event("more2_lava_near_building").event("more2_hazard_flow");
        world.with("cobblestone", 0).with("dirt", 0).with("sand", 0).with("gravel", 0);
        GraphPlan plan = plan(world, facts(TWO));
        GraphPlan.Task lava = task(plan, "event:more2_lava_near_building:more2_remove_lava");
        GraphPlan.Task block = task(plan, "event:more2_hazard_flow:more2_block_flow");
        assertTrue(
                lava.feasible(),
                "Лава у шахты — работа убрать её выполнима:  Иначе тест case30_LavaNearMine_BlockingAndStoneRaisedBeforeWork перестанет ловить эту поломку. При смене case30_LavaNearMine_BlockingAndStoneRaisedBeforeWork проверь это утверждение."
                        + ids(plan));
        assertFalse(
                block.feasible(),
                "Поток без блоков не перекрыть:  Иначе тест case30_LavaNearMine_BlockingAndStoneRaisedBeforeWork перестанет ловить эту поломку. При смене case30_LavaNearMine_BlockingAndStoneRaisedBeforeWork проверь это утверждение."
                        + ids(plan));
        assertTrue(
                task(plan, "mine_cobble").raised(),
                "Блоков нет — поднимается камень:  Иначе тест case30_LavaNearMine_BlockingAndStoneRaisedBeforeWork перестанет ловить эту поломку. При смене case30_LavaNearMine_BlockingAndStoneRaisedBeforeWork проверь это утверждение."
                        + ids(plan));
        assertTrue(
                task(plan, "dig_dirt").raised(),
                "Земля тоже источник блоков:  Иначе тест case30_LavaNearMine_BlockingAndStoneRaisedBeforeWork перестанет ловить эту поломку. При смене case30_LavaNearMine_BlockingAndStoneRaisedBeforeWork проверь это утверждение."
                        + ids(plan));
        world.with("cobblestone", 160).with("dirt", 160).with("sand", 128);
        GraphPlan stocked = plan(world, facts(TWO));
        assertTrue(
                task(stocked, "event:more2_hazard_flow:more2_block_flow").feasible(),
                "Блоки есть — поток перекрывается:  Иначе тест case30_LavaNearMine_BlockingAndStoneRaisedBeforeWork перестанет ловить эту поломку. При смене case30_LavaNearMine_BlockingAndStoneRaisedBeforeWork проверь это утверждение."
                        + ids(stocked));
        assertTrue(
                stocked.task("mine_cobble").isEmpty(),
                "Камня хватает — шахта снята:  Иначе тест case30_LavaNearMine_BlockingAndStoneRaisedBeforeWork перестанет ловить эту поломку. При смене case30_LavaNearMine_BlockingAndStoneRaisedBeforeWork проверь это утверждение."
                        + ids(stocked));
        assertTrue(
                stocked.task("dig_dirt").isEmpty(),
                "Земли хватает — добыча снята:  Иначе тест case30_LavaNearMine_BlockingAndStoneRaisedBeforeWork перестанет ловить эту поломку. При смене case30_LavaNearMine_BlockingAndStoneRaisedBeforeWork проверь это утверждение."
                        + ids(stocked));
    }

    private static List<String> ids(GraphPlan plan) {
        return plan.tasks().stream().map(GraphPlan.Task::id).toList();
    }

    private static GraphWorld pair(GraphWorld world) {
        world.tools("spirit-2", 1);
        return world;
    }
}
