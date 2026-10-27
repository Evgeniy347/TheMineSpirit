package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import org.junit.jupiter.api.Test;

class GraphPlannerTests {
    @Test
    void plan_NoCoal_PostsMineAndCharcoal() {
        GraphPlan plan = GraphWorld.settled().with("coal", 0).plan();

        assertTrue(
                plan.task("mine_coal").isPresent()
                        && plan.task("smelt_charcoal").isPresent(),
                "Нет угля — на доске оба пути: «добыть каменный уголь» и «добыть древесный уголь», получено "
                        + plan.tasks());
    }

    @Test
    void plan_NoTorches_RaisesCoalWays() {
        GraphPlan stocked =
                GraphWorld.settled().with("coal", 0).with("torch", 64).plan();
        GraphPlan dark = GraphWorld.settled().with("coal", 0).with("torch", 0).plan();

        assertTrue(
                dark.priority("mine_coal") > stocked.priority("mine_coal")
                        && dark.priority("smelt_charcoal") > stocked.priority("smelt_charcoal"),
                "Нет факелов — уголь обоими путями важнее: " + dark.priority("mine_coal") + " против "
                        + stocked.priority("mine_coal"));
    }

    @Test
    void plan_NoIronPickaxe_LowersMineCoal() {
        GraphPlan iron =
                GraphWorld.settled().tools("spirit-1", 2).with("coal", 0).plan();
        GraphPlan stone = GraphWorld.settled().with("coal", 0).plan();

        assertTrue(
                stone.priority("mine_coal") < iron.priority("mine_coal"),
                "Нет железной кирки — каменный уголь ниже: " + stone.priority("mine_coal") + " против "
                        + iron.priority("mine_coal"));
    }

    @Test
    void plan_NoStonePickaxe_RaisesCobbleAndSticks() {
        GraphPlan has =
                GraphWorld.settled().with("cobblestone", 0).with("stick", 0).plan();
        GraphPlan lacks = GraphWorld.settled()
                .tools("spirit-1", 0)
                .with("cobblestone", 0)
                .with("stick", 0)
                .plan();

        assertTrue(
                lacks.priority("mine_cobble") > has.priority("mine_cobble")
                        && lacks.priority("craft_stick") > has.priority("craft_stick"),
                "Нет каменной кирки — булыжник и палки важнее: " + lacks.tasks());
    }

    @Test
    void plan_NoWoodenTools_RaisesLogAndSticks() {
        GraphPlan has = GraphWorld.settled()
                .tools("spirit-1", 0)
                .with("oak_log", 0)
                .with("oak_planks", 0)
                .with("stick", 0)
                .plan();
        GraphPlan lacks = GraphWorld.settled()
                .tools("spirit-1", -1)
                .with("oak_log", 0)
                .with("oak_planks", 0)
                .with("stick", 0)
                .plan();

        assertTrue(
                lacks.priority("chop_log") > has.priority("chop_log")
                        && lacks.priority("craft_stick") > has.priority("craft_stick"),
                "Нет деревянной кирки и топора — дерево и палки важнее: " + lacks.priority("chop_log") + " против "
                        + has.priority("chop_log"));
    }

    @Test
    void plan_LogLacking_AxesOrderedIronStoneWooden() {
        GraphPlan plan = GraphWorld.empty().with("oak_log", 0).plan();

        int iron = plan.priority("craft_iron_axe@spirit-1");
        int stone = plan.priority("craft_stone_axe@spirit-1");
        int wooden = plan.priority("craft_wooden_axe@spirit-1");
        assertTrue(
                iron > stone && stone > wooden,
                "Топоры: железный > каменный > деревянный: " + iron + ", " + stone + ", " + wooden);
    }

    @Test
    void issueOrder_OnlyPlanksAvailable_WoodenAxeFirstAmongAxes() {
        GraphPlan plan = GraphWorld.empty()
                .built("crafting_table", Built.BUILT)
                .with("oak_planks", 8)
                .with("stick", 4)
                .plan();

        String first = GraphWorld.issueOrder(plan).stream()
                .filter(task -> task.id().startsWith("craft_") && task.id().contains("_axe"))
                .findFirst()
                .map(GraphPlan.Task::id)
                .orElse("нет");
        assertEquals("craft_wooden_axe@spirit-1", first, "Делается тот топор, что можно сделать сейчас");
    }

    @Test
    void plan_LogsLackingSaplingsHeld_OrchardRaised() {
        GraphPlan without =
                GraphWorld.settled().with("oak_log", 64).with("oak_sapling", 4).plan();
        GraphPlan lacking =
                GraphWorld.settled().with("oak_log", 0).with("oak_sapling", 4).plan();

        assertTrue(
                lacking.priority("building:orchard") > without.priority("building:orchard"),
                "Брёвен нет, саженцы есть — сад важнее: " + lacking.priority("building:orchard") + " против "
                        + without.priority("building:orchard"));
    }

    @Test
    void plan_StorageFullWarehouseNotPlanned_NoticeForStrategist() {
        GraphWorld world = GraphWorld.settled();
        world.storageFree = 5;
        GraphPlan plan = world.plan();

        assertTrue(
                plan.notices().stream().anyMatch(notice -> notice.building().equals("warehouse")),
                "Склад полон, склада нет в плане — просьба стратегу: " + plan.notices());
    }

    @Test
    void plan_StockAboveThreshold_NoGatherTask() {
        GraphPlan plan = GraphWorld.settled().with("dirt", 1000).plan();

        assertTrue(
                plan.task("dig_dirt").isEmpty()
                        || plan.task("dig_dirt").orElseThrow().priority() == 0,
                "Земли больше Xmin·P·S·K — задания «добыть землю» нет: " + plan.task("dig_dirt"));
    }
}
