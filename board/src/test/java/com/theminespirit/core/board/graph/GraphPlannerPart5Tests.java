package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import org.junit.jupiter.api.Test;

class GraphPlannerPart5Tests {
    @Test
    void plan_PortalHallBuiltLighterHeld_LightingFeasible() {
        GraphWorld world = GraphWorld.settled()
                .with("flint_and_steel", 1)
                .built("portal_hall", Built.BUILT)
                .built("portal_lit", Built.PLANNED);
        world.stage = 3;
        GraphPlan plan = world.plan();
        assertTrue(
                plan.task("building:portal_lit").map(GraphPlan.Task::feasible).orElse(false),
                "Зал готов и огниво есть — задание «зажечь портал» выполнимо: "
                        + plan.task(
                                "building:portal_lit Иначе тест plan_PortalHallBuiltLighterHeld_LightingFeasible перестанет ловить эту поломку. При смене plan_PortalHallBuiltLighterHeld_LightingFeasible проверь это утверждение."));
    }

    @Test
    void plan_OakAndJungleNearby_ChopsBothSpecies() {
        GraphPlan plan =
                GraphWorld.settled().with("oak_log", 0).with("jungle_log", 0).plan();
        assertTrue(
                plan.task("chop_oak_log").isPresent()
                        && plan.task("chop_jungle_log").isPresent()
                        && plan.task("chop_birch_log").isEmpty(),
                "Рядом дуб и тропическое дерево — брёвна заготавливаются обеих пород, берёзы рядом нет — её не рубим: "
                        + plan.tasks().stream()
                                .map(GraphPlan.Task::id)
                                .filter(
                                        id -> id.startsWith(
                                                "chop_ Иначе тест plan_OakAndJungleNearby_ChopsBothSpecies перестанет ловить эту поломку. При смене plan_OakAndJungleNearby_ChopsBothSpecies проверь это утверждение."))
                                .toList());
    }

    @Test
    void plan_BirchSaplingHeldNoBirchOrchard_NoticeForStrategist() {
        GraphPlan plan = GraphWorld.settled().with("birch_sapling", 2).plan();
        assertTrue(
                plan.notices().stream().anyMatch(notice -> "orchard_birch".equals(notice.building())),
                "Появились саженцы берёзы, сада под берёзу в плане нет — просьба стратегу поставить сад:  Иначе тест plan_BirchSaplingHeldNoBirchOrchard_NoticeForStrategist перестанет ловить эту поломку. При смене plan_BirchSaplingHeldNoBirchOrchard_NoticeForStrategist проверь это утверждение."
                        + plan.notices());
    }

    @Test
    void plan_JungleSaplingsOrchardPlanned_SecondOrchardBuilt() {
        GraphPlan plan = GraphWorld.settled()
                .with("jungle_sapling", 4)
                .built("orchard_jungle", Built.PLANNED)
                .plan();
        assertTrue(
                plan.task("building:orchard_oak").isPresent()
                        && plan.task("building:orchard_jungle").isPresent(),
                "Дуб и тропическое дерево — два сада, под каждую породу: "
                        + plan.tasks().stream()
                                .map(GraphPlan.Task::id)
                                .filter(
                                        id -> id.startsWith(
                                                "building:orchard Иначе тест plan_JungleSaplingsOrchardPlanned_SecondOrchardBuilt перестанет ловить эту поломку. При смене plan_JungleSaplingsOrchardPlanned_SecondOrchardBuilt проверь это утверждение."))
                                .toList());
    }

    @Test
    void plan_OnlyJunglePlanks_SticksFromAnyPlanks() {
        GraphPlan plan = GraphWorld.settled()
                .with("oak_planks", 0)
                .with("jungle_planks", 10)
                .with("stick", 0)
                .plan();
        assertTrue(
                plan.task("craft_stick").map(GraphPlan.Task::feasible).orElse(false),
                "Палки делаются из досок любой породы: тропические доски годятся — "
                        + plan.task(
                                "craft_stick Иначе тест plan_OnlyJunglePlanks_SticksFromAnyPlanks перестанет ловить эту поломку. При смене plan_OnlyJunglePlanks_SticksFromAnyPlanks проверь это утверждение."));
    }

    @Test
    void plan_DropsNearby_CollectTask() {
        GraphPlan plan = GraphWorld.settled().event("drops_nearby").plan();
        assertTrue(
                plan.task("event:drops_nearby:collect_drops").isPresent(),
                "В поселении и округе лежат предметы — задание собрать: "
                        + plan.tasks().stream()
                                .map(GraphPlan.Task::id)
                                .filter(
                                        id -> id.startsWith(
                                                "event: Иначе тест plan_DropsNearby_CollectTask перестанет ловить эту поломку. При смене plan_DropsNearby_CollectTask проверь это утверждение."))
                                .toList());
    }

    @Test
    void plan_LeafDecay_CollectSaplingsTask() {
        GraphPlan plan = GraphWorld.settled().event("leaf_decay").plan();
        assertTrue(
                plan.task("event:leaf_decay:collect_saplings").isPresent(),
                "Листва опадает, падают саженцы — задание подобрать саженцы:  Иначе тест plan_LeafDecay_CollectSaplingsTask перестанет ловить эту поломку. При смене plan_LeafDecay_CollectSaplingsTask проверь это утверждение."
                        + plan.advice());
    }

    @Test
    void plan_OnlyCharcoal_TorchesFeasible() {
        GraphPlan plan = GraphWorld.settled()
                .with("coal", 0)
                .with("charcoal", 4)
                .with("torch", 0)
                .plan();
        assertTrue(
                plan.task("craft_torch").map(GraphPlan.Task::feasible).orElse(false),
                "Факелы — из каменного или древесного угля: один древесный годится — "
                        + plan.task(
                                "craft_torch Иначе тест plan_OnlyCharcoal_TorchesFeasible перестанет ловить эту поломку. При смене plan_OnlyCharcoal_TorchesFeasible проверь это утверждение."));
    }

    @Test
    void plan_OnlyDeepslate_StoneToolsFeasible() {
        GraphPlan plan = GraphWorld.settled()
                .tools("spirit-1", 0)
                .with("cobblestone", 0)
                .with("cobbled_deepslate", 8)
                .plan();
        assertTrue(
                plan.task("craft_stone_pickaxe@spirit-1")
                        .map(GraphPlan.Task::feasible)
                        .orElse(false),
                "Каменные инструменты — из любого камня группы: сланец годится — "
                        + plan.task(
                                "craft_stone_pickaxe@spirit-1 Иначе тест plan_OnlyDeepslate_StoneToolsFeasible перестанет ловить эту поломку. При смене plan_OnlyDeepslate_StoneToolsFeasible проверь это утверждение."));
    }

    @Test
    void plan_MixedWoolColors_BedNeedsThreeOfOneColor() {
        GraphWorld world = GraphWorld.settled().with("red_wool", 1).with("white_wool", 2);
        world.stage = 3;
        GraphPlan mixed = world.plan();
        GraphPlan red = world.copy().with("red_wool", 3).plan();
        assertTrue(
                mixed.tasks().stream().noneMatch(task -> task.id().endsWith("_bed") && task.feasible())
                        && red.task("craft_red_bed")
                                .map(GraphPlan.Task::feasible)
                                .orElse(false),
                "Кровать — три шерсти одного цвета: 1 красная + 2 белые не годятся, 3 красные — годятся Иначе тест plan_MixedWoolColors_BedNeedsThreeOfOneColor перестанет ловить эту поломку. При смене plan_MixedWoolColors_BedNeedsThreeOfOneColor проверь это утверждение.");
    }
}
