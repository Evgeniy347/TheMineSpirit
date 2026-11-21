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
                "Нет угля — на доске оба пути: «добыть каменный уголь» и «добыть древесный уголь», получено  Иначе тест plan_NoCoal_PostsMineAndCharcoal перестанет ловить эту поломку. При смене plan_NoCoal_PostsMineAndCharcoal проверь это утверждение."
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
                        + stocked.priority(
                                "mine_coal Иначе тест plan_NoTorches_RaisesCoalWays перестанет ловить эту поломку. При смене plan_NoTorches_RaisesCoalWays проверь это утверждение."));
    }

    @Test
    void plan_NoIronPickaxe_LowersMineCoal() {
        GraphPlan iron =
                GraphWorld.settled().tools("spirit-1", 2).with("coal", 0).plan();
        GraphPlan stone = GraphWorld.settled().with("coal", 0).plan();
        assertTrue(
                stone.priority("mine_coal") < iron.priority("mine_coal"),
                "Нет железной кирки — каменный уголь ниже: " + stone.priority("mine_coal") + " против "
                        + iron.priority(
                                "mine_coal Иначе тест plan_NoIronPickaxe_LowersMineCoal перестанет ловить эту поломку. При смене plan_NoIronPickaxe_LowersMineCoal проверь это утверждение."));
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
                "Нет каменной кирки — булыжник и палки важнее:  Иначе тест plan_NoStonePickaxe_RaisesCobbleAndSticks перестанет ловить эту поломку. При смене plan_NoStonePickaxe_RaisesCobbleAndSticks проверь это утверждение."
                        + lacks.tasks());
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
                GraphWorld.best(lacks, "chop_") > GraphWorld.best(has, "chop_")
                        && lacks.priority("craft_stick") > has.priority("craft_stick"),
                "Нет деревянной кирки и топора — рубка (любой породы, граф выбирает породу сам) и палки важнее: "
                        + GraphWorld.best(lacks, "chop_") + " против "
                        + GraphWorld.best(
                                has,
                                "chop_ Иначе тест plan_NoWoodenTools_RaisesLogAndSticks перестанет ловить эту поломку. При смене plan_NoWoodenTools_RaisesLogAndSticks проверь это утверждение."));
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
        assertEquals(
                "craft_wooden_axe@spirit-1",
                first,
                "Делается тот топор, что можно сделать сейчас Иначе тест issueOrder_OnlyPlanksAvailable_WoodenAxeFirstAmongAxes перестанет ловить эту поломку. При смене issueOrder_OnlyPlanksAvailable_WoodenAxeFirstAmongAxes проверь это утверждение.");
    }

    @Test
    void plan_LogsLackingSaplingsHeld_OrchardRaised() {
        GraphPlan without =
                GraphWorld.settled().with("oak_log", 64).with("oak_sapling", 4).plan();
        GraphPlan lacking =
                GraphWorld.settled().with("oak_log", 0).with("oak_sapling", 4).plan();
        assertTrue(
                lacking.priority("building:orchard_oak") > without.priority("building:orchard_oak"),
                "Брёвен нет, саженцы есть — сад важнее: " + lacking.priority("building:orchard_oak") + " против "
                        + without.priority(
                                "building:orchard_oak Иначе тест plan_LogsLackingSaplingsHeld_OrchardRaised перестанет ловить эту поломку. При смене plan_LogsLackingSaplingsHeld_OrchardRaised проверь это утверждение."));
    }
}
