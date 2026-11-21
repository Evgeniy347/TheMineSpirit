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

class DemandWorkChainScenarioTests {
    private static final Map<String, GraphFacts.Spirit> TWO = named(spirit("spirit-1"), spirit("spirit-2"));

    @Test
    void case34_OreVeinEnded_SustainMineRaised() {
        GraphWorld world = pair(stageTwo()).world("more:ore_depleted").built("mine", Built.BUILT);
        world.with("cobblestone", 80).with("coal", 40);
        GraphPlan plan = plan(world, facts(TWO));

        GraphPlan.Task sustain = task(plan, "more_sustain_mine");
        assertTrue(
                sustain.feasible(),
                "Жила кончилась — граф поднимает смену жилы/шахты:  Иначе тест case34_OreVeinEnded_SustainMineRaised перестанет ловить эту поломку. При смене case34_OreVeinEnded_SustainMineRaised проверь это утверждение."
                        + ids(plan));
        world.noWorld("more:ore_depleted");
        GraphPlan alive = plan(world, facts(TWO));
        assertTrue(
                alive.task("more_sustain_mine").isEmpty(),
                "Жила есть — смена шахты не заказывается:  Иначе тест case34_OreVeinEnded_SustainMineRaised перестанет ловить эту поломку. При смене case34_OreVeinEnded_SustainMineRaised проверь это утверждение."
                        + ids(alive));
    }

    @Test
    void case35_BricksMissingForForge_ClayAndFurnaceRaised() {
        GraphWorld world = pair(stageTwo());
        world.stage = 3;
        world.built("forge", Built.PLANNED);
        world.with("bricks", 0).with("clay_ball", 0).with("cobblestone", 0).with("iron_ingot", 0);
        GraphPlan plan = plan(world, facts(TWO));
        GraphPlan.Task forge = task(plan, "building:forge");
        GraphPlan.Task bricks = task(plan, "fire_bricks");
        GraphPlan.Task clay = task(plan, "dig_clay");
        assertFalse(
                forge.feasible(),
                "Кирпича нет — кузня ждёт материал:  Иначе тест case35_BricksMissingForForge_ClayAndFurnaceRaised перестанет ловить эту поломку. При смене case35_BricksMissingForForge_ClayAndFurnaceRaised проверь это утверждение."
                        + ids(plan));
        assertTrue(
                bricks.raised(),
                "Кирпича нет — поднимается обжиг кирпича:  Иначе тест case35_BricksMissingForForge_ClayAndFurnaceRaised перестанет ловить эту поломку. При смене case35_BricksMissingForForge_ClayAndFurnaceRaised проверь это утверждение."
                        + ids(plan));
        assertTrue(
                clay.feasible(),
                "Глины нет — поднимается добыча глины:  Иначе тест case35_BricksMissingForForge_ClayAndFurnaceRaised перестанет ловить эту поломку. При смене case35_BricksMissingForForge_ClayAndFurnaceRaised проверь это утверждение."
                        + ids(plan));
        world.with("bricks", 64).with("clay_ball", 64).with("cobblestone", 80).with("iron_ingot", 8);
        GraphPlan stocked = plan(world, facts(TWO));
        assertTrue(
                task(stocked, "building:forge").feasible(),
                "Материалы есть — кузня строится:  Иначе тест case35_BricksMissingForForge_ClayAndFurnaceRaised перестанет ловить эту поломку. При смене case35_BricksMissingForForge_ClayAndFurnaceRaised проверь это утверждение."
                        + ids(stocked));
        assertTrue(
                stocked.task("fire_bricks").isEmpty(),
                "Кирпича хватает — обжиг снят:  Иначе тест case35_BricksMissingForForge_ClayAndFurnaceRaised перестанет ловить эту поломку. При смене case35_BricksMissingForForge_ClayAndFurnaceRaised проверь это утверждение."
                        + ids(stocked));
        assertTrue(
                stocked.task("dig_clay").isEmpty(),
                "Глины хватает — добыча снята:  Иначе тест case35_BricksMissingForForge_ClayAndFurnaceRaised перестанет ловить эту поломку. При смене case35_BricksMissingForForge_ClayAndFurnaceRaised проверь это утверждение."
                        + ids(stocked));
    }

    @Test
    void case37_ColoredBedPlan_DyeAndFlowersRaised() {
        GraphWorld world = pair(stageTwo());
        world.with("red_wool", 0).with("white_wool", 8).with("red_dye", 0).with("poppy", 8);
        GraphPlan plan = ordered(world, facts(TWO), Map.of("red_wool", 3));
        GraphPlan.Task wool = task(plan, "dye_red_wool");
        assertFalse(
                wool.feasible(),
                "Красителя нет — красная шерсть ждёт краситель:  Иначе тест case37_ColoredBedPlan_DyeAndFlowersRaised перестанет ловить эту поломку. При смене case37_ColoredBedPlan_DyeAndFlowersRaised проверь это утверждение."
                        + ids(plan));
        assertTrue(
                task(plan, "craft_red_dye").raised(),
                "Красителя нет — поднимается крафт красителя:  Иначе тест case37_ColoredBedPlan_DyeAndFlowersRaised перестанет ловить эту поломку. При смене case37_ColoredBedPlan_DyeAndFlowersRaised проверь это утверждение."
                        + ids(plan));
        world.with("red_dye", 8);
        GraphPlan stocked = ordered(world, facts(TWO), Map.of("red_wool", 3));
        assertTrue(
                task(stocked, "dye_red_wool").feasible(),
                "Краситель есть — шерсть красится:  Иначе тест case37_ColoredBedPlan_DyeAndFlowersRaised перестанет ловить эту поломку. При смене case37_ColoredBedPlan_DyeAndFlowersRaised проверь это утверждение."
                        + ids(stocked));
        assertTrue(
                stocked.task("craft_red_dye").isEmpty(),
                "Красителя хватает — крафт снят:  Иначе тест case37_ColoredBedPlan_DyeAndFlowersRaised перестанет ловить эту поломку. При смене case37_ColoredBedPlan_DyeAndFlowersRaised проверь это утверждение."
                        + ids(stocked));
    }

    @Test
    void case38_LeatherMissing_CowSlaughterRaised() {
        GraphWorld world = pair(stageTwo()).built("pen_cow", Built.BUILT);
        world.with("herd_cow", 3).with("leather", 0);
        GraphPlan plan = plan(world, facts(TWO));
        GraphPlan.Task skin = task(plan, "skin_cow");
        assertTrue(
                skin.feasible(),
                "Кожи нет — граф поднимает забой коровы:  Иначе тест case38_LeatherMissing_CowSlaughterRaised перестанет ловить эту поломку. При смене case38_LeatherMissing_CowSlaughterRaised проверь это утверждение."
                        + ids(plan));
        world.with("leather", 24).with("leather_armor", 4);
        GraphPlan stocked = plan(world, facts(TWO));
        assertTrue(
                stocked.task("skin_cow").isEmpty(),
                "Кожи и брони хватает — забой снят:  Иначе тест case38_LeatherMissing_CowSlaughterRaised перестанет ловить эту поломку. При смене case38_LeatherMissing_CowSlaughterRaised проверь это утверждение."
                        + ids(stocked));
    }

    private static GraphPlan ordered(GraphWorld world, GraphFacts facts, Map<String, Integer> orders) {
        return GraphPlanner.plan(
                world.graph,
                world.state().withFacts(facts, world.graph.costs().life()).withOrders(orders));
    }

    private static List<String> ids(GraphPlan plan) {
        return plan.tasks().stream().map(GraphPlan.Task::id).toList();
    }

    private static GraphWorld pair(GraphWorld world) {
        world.tools("spirit-2", 1);
        return world;
    }
}
