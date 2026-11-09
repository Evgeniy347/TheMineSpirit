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
        assertTrue(sustain.feasible(), "Жила кончилась — граф поднимает смену жилы/шахты: " + ids(plan));

        world.noWorld("more:ore_depleted");
        GraphPlan alive = plan(world, facts(TWO));
        assertTrue(alive.task("more_sustain_mine").isEmpty(), "Жила есть — смена шахты не заказывается: " + ids(alive));
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
        assertFalse(forge.feasible(), "Кирпича нет — кузня ждёт материал: " + ids(plan));
        assertTrue(bricks.priority() > 0, "Кирпича нет — поднимается обжиг кирпича: " + ids(plan));
        assertTrue(clay.feasible(), "Глины нет — поднимается добыча глины: " + ids(plan));

        world.with("bricks", 64).with("clay_ball", 64).with("cobblestone", 80).with("iron_ingot", 8);
        GraphPlan stocked = plan(world, facts(TWO));
        assertTrue(task(stocked, "building:forge").feasible(), "Материалы есть — кузня строится: " + ids(stocked));
        assertTrue(stocked.task("fire_bricks").isEmpty(), "Кирпича хватает — обжиг снят: " + ids(stocked));
        assertTrue(stocked.task("dig_clay").isEmpty(), "Глины хватает — добыча снята: " + ids(stocked));
    }

    @Test
    void case37_ColoredBedPlan_DyeAndFlowersRaised() {
        GraphWorld world = pair(stageTwo());
        world.with("red_wool", 0).with("white_wool", 8).with("red_dye", 0).with("poppy", 8);
        GraphPlan plan = ordered(world, facts(TWO), Map.of("red_wool", 3));

        GraphPlan.Task wool = task(plan, "dye_red_wool");
        assertFalse(wool.feasible(), "Красителя нет — красная шерсть ждёт краситель: " + ids(plan));
        assertTrue(
                task(plan, "craft_red_dye").priority() > 0,
                "Красителя нет — поднимается крафт красителя: " + ids(plan));

        world.with("red_dye", 8);
        GraphPlan stocked = ordered(world, facts(TWO), Map.of("red_wool", 3));
        assertTrue(task(stocked, "dye_red_wool").feasible(), "Краситель есть — шерсть красится: " + ids(stocked));
        assertTrue(stocked.task("craft_red_dye").isEmpty(), "Красителя хватает — крафт снят: " + ids(stocked));
    }

    @Test
    void case38_LeatherMissing_CowSlaughterRaised() {
        GraphWorld world = pair(stageTwo()).built("pen_cow", Built.BUILT);
        world.with("herd_cow", 3).with("leather", 0);
        GraphPlan plan = plan(world, facts(TWO));

        GraphPlan.Task skin = task(plan, "skin_cow");
        assertTrue(skin.feasible(), "Кожи нет — граф поднимает забой коровы: " + ids(plan));

        world.with("leather", 24).with("leather_armor", 4);
        GraphPlan stocked = plan(world, facts(TWO));
        assertTrue(stocked.task("skin_cow").isEmpty(), "Кожи и брони хватает — забой снят: " + ids(stocked));
    }

    @Test
    void case39_DiamondForStageThree_DeeperMineRaised() {
        GraphWorld world = pair(stageTwo());
        world.stage = 3;
        world.built("mine_level_3", Built.BUILT)
                .built("mine_level_4", Built.PLANNED)
                .with("diamond", 0);
        GraphPlan plan = ordered(world, facts(TWO), Map.of("diamond", 8));

        GraphPlan.Task deep = task(plan, "building:mine_level_4");
        GraphPlan.Task mine = task(plan, "mine_diamond");
        assertTrue(deep.priority() > 0, "Алмаза нет — поднимается более глубокая шахта: " + ids(plan));
        assertTrue(mine.priority() > 0, "Добыча алмаза поднята заказом этапа: " + ids(plan));

        world.built("mine_level_4", Built.BUILT).with("diamond", 64);
        GraphPlan stocked = plan(world, facts(TWO));
        assertTrue(
                stocked.task("building:mine_level_4").isEmpty(),
                "Глубокая шахта есть — стройка снята: " + ids(stocked));
        assertTrue(stocked.task("mine_diamond").isEmpty(), "Алмаза хватает — добыча снята: " + ids(stocked));
    }

    @Test
    void case45_UnreachableResource_ExplorationAndBridgeRaised() {
        GraphWorld missing = pair(stageTwo()).noWorld("emerald_ore");
        GraphPlan plan = ordered(missing, facts(TWO), Map.of("emerald", 2));

        GraphPlan.Task explore = task(plan, "explore:emerald_ore");
        assertTrue(explore.feasible(), "Руды нет в округе — граф поднимает разведку: " + ids(plan));
        assertTrue(
                plan.notices().stream().anyMatch(notice -> notice.building().contains("emerald")),
                "Граф объясняет, что источника изумруда нет: " + plan.notices());

        GraphWorld found = pair(stageTwo()).world("emerald_ore");
        GraphPlan known = ordered(found, facts(TWO), Map.of("emerald", 2));
        assertTrue(known.task("explore:emerald_ore").isEmpty(), "Руда найдена — разведка не нужна: " + ids(known));

        GraphWorld bridge = pair(stageTwo()).event("more5_route_blocked").with("oak_planks", 0);
        GraphPlan route = plan(bridge, facts(TWO));
        assertFalse(
                task(route, "event:more5_route_blocked:more5_bridge_route").feasible(),
                "Пути нет — мост ждёт доски: " + ids(route));
        assertTrue(
                task(route, "craft_oak_planks").priority() > 0, "Досок нет — поднимается крафт досок: " + ids(route));
    }

    @Test
    void case46_TrailsSlow_TrailAndBlocksRaised() {
        GraphWorld world = pair(stageTwo()).event("more2_path_needed");
        world.with("cobblestone", 0).with("dirt", 0).with("sand", 0).with("gravel", 0);
        GraphPlan plan = plan(world, facts(TWO));

        GraphPlan.Task trail = task(plan, "event:more2_path_needed:more2_build_trail");
        assertFalse(trail.feasible(), "Блоков нет — тропа ждёт материал: " + ids(plan));
        assertTrue(task(plan, "mine_cobble").priority() > 0, "Камня нет — поднимается шахта: " + ids(plan));
        assertTrue(task(plan, "dig_dirt").priority() > 0, "Земли нет — поднимается добыча: " + ids(plan));

        world.with("cobblestone", 160).with("dirt", 160);
        GraphPlan stocked = plan(world, facts(TWO));
        assertTrue(
                task(stocked, "event:more2_path_needed:more2_build_trail").feasible(),
                "Блоки есть — тропа строится: " + ids(stocked));
        assertTrue(stocked.task("mine_cobble").isEmpty(), "Камня хватает — шахта снята: " + ids(stocked));
        assertTrue(stocked.task("dig_dirt").isEmpty(), "Земли хватает — добыча снята: " + ids(stocked));
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
