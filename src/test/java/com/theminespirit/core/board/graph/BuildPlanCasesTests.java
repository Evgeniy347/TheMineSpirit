package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuildPlanCasesTests {
    private static final String HUT_REPAIR = "event:repair_hut:repair:hut";

    @Test
    void plan_AuditComplete_DoesNotRebuild() {
        GraphWorld world = GraphWorld.settled();
        world.buildings.put("hut", Built.PLANNED);
        GraphFacts facts = PlanCaseFacts.facts(
                Map.of("hut", PlanCaseFacts.building("hut", 100, 0, Map.of(), false)),
                List.of(),
                GraphSiteFacts.Terrain.empty());

        GraphPlan plan = PlanCaseFacts.plan(world, facts);

        assertFalse(
                PlanCaseFacts.posted(plan, "building:hut"),
                "Аудит показал 100% — хижину не строят заново, даже если журнал доски этого ещё не записал: "
                        + plan.tasks());
        assertTrue(
                GraphStageGate.buildingReady(facts, "hut"),
                "Целая известная постройка по аудиту закрывает свой шаг этапа, а не ждёт повторной стройки");
    }

    @Test
    void plan_PartialBuilding_RepairsMissingInsteadOfRebuild() {
        GraphWorld world = GraphWorld.settled().with("dirt", 5);
        world.buildings.put("hut", Built.PLANNED);
        world.materials.put("hut", Map.of("dirt", 5));
        GraphFacts facts = PlanCaseFacts.facts(
                Map.of("hut", PlanCaseFacts.building("hut", 95, 5, Map.of("minecraft:dirt", 5), false)),
                List.of(),
                GraphSiteFacts.Terrain.empty());

        GraphPlan plan = PlanCaseFacts.plan(world, facts);

        assertFalse(
                PlanCaseFacts.posted(plan, "building:hut"),
                "Дом на 95% не строят заново: полная стройка ломает план, меняет породу и роняет процент "
                        + "обратно — нужен ремонт недостающих клеток: " + plan.tasks());
        GraphPlan.Task repair = PlanCaseFacts.task(plan, HUT_REPAIR);
        assertTrue(repair.feasible(), "Ремонт частично построенного дома выполним по аудиту: " + repair);
        assertEquals(
                Map.of("dirt", 5),
                repair.consumes(),
                "Ремонт просит только недостающие блоки аудита, а не весь набор схемы: иначе дух тащит "
                        + "десятки лишних предметов");
    }

    @Test
    void plan_UnknownAudit_DoesNotRebuildBlindly() {
        GraphWorld world = GraphWorld.settled();
        world.buildings.put("hut", Built.PLANNED);
        GraphFacts facts = PlanCaseFacts.facts(
                Map.of("hut", PlanCaseFacts.building("hut", 0, 5, Map.of("minecraft:dirt", 5), true)),
                List.of(),
                GraphSiteFacts.Terrain.empty());

        GraphPlan plan = PlanCaseFacts.plan(world, facts);

        assertFalse(
                PlanCaseFacts.posted(plan, "building:hut"),
                "Незагруженный чанк — «неизвестно», а не «не построено»: слепая полная стройка снесла бы "
                        + "готовые клетки, поэтому работа ждёт аудита: " + plan.tasks());
        assertFalse(
                GraphStageGate.buildingReady(facts, "hut"),
                "Неизвестная постройка не закрывает шаг этапа: «неизвестно» = не готово");
    }

    @Test
    void stageGate_PartialBuilding_KeepsStageOpen() {
        GraphFacts facts = PlanCaseFacts.facts(
                Map.of(
                        "hut", PlanCaseFacts.building("hut", 100, 0, Map.of(), false),
                        "mine", PlanCaseFacts.building("mine", 95, 5, Map.of("minecraft:cobblestone", 5), false)),
                List.of(),
                GraphSiteFacts.Terrain.empty());

        assertTrue(GraphStageGate.buildingReady(facts, "hut"), "Готовая постройка этапа закрыта");
        assertFalse(
                GraphStageGate.buildingReady(facts, "mine"),
                "Этап закрывается только когда все его постройки 100%: шахта на 95% держит этап открытым");
    }

    @Test
    void repair_OtherBuildingSchemeChanged_LeavesThisRepairIntact() {
        GraphWorld world = GraphWorld.settled();
        world.buildings.put("mine", Built.BUILT);
        world.buildings.put("hut", Built.BUILT);
        world.materials.put("mine", Map.of("cobblestone", 3));
        world.materials.put("hut", Map.of("dirt", 2));
        GraphFacts facts = PlanCaseFacts.facts(
                Map.of(
                        "mine", PlanCaseFacts.building("mine", 90, 3, Map.of("minecraft:cobblestone", 3), false),
                        "hut", PlanCaseFacts.building("hut", 95, 2, Map.of("minecraft:dirt", 2), false)),
                List.of(),
                GraphSiteFacts.Terrain.empty());
        GraphPlan before = PlanCaseFacts.plan(world, facts);

        world.materials.put("hut", Map.of("oak_log", 40));
        GraphPlan after = PlanCaseFacts.plan(world, facts);

        GraphPlan.Task first = PlanCaseFacts.task(before, "event:repair_mine:repair:mine");
        GraphPlan.Task second = PlanCaseFacts.task(after, "event:repair_mine:repair:mine");
        assertEquals(
                first.consumes(),
                second.consumes(),
                "Смена схемы хижины не меняет расход ремонта шахты: постройки чинятся по своим недостачам");
        assertEquals(
                first.priority(),
                second.priority(),
                "Смена схемы хижины не двигает вес ремонта шахты: иначе одна постройка вытесняет другую с доски");
    }

    @Test
    void plan_StrayBesidePlannedHouse_DemolishesOnlyStray() {
        GraphWorld world = GraphWorld.settled();
        GraphFacts facts = PlanCaseFacts.facts(
                Map.of(),
                List.of(new GraphSiteFacts.Stray(
                        "stray:oak_fence@1,69,1", "oak_fence", new GraphFacts.Position(1, 69, 1), "spirit-1")),
                GraphSiteFacts.Terrain.empty());

        GraphPlan plan = PlanCaseFacts.plan(world, facts);

        GraphPlan.Task demolition = PlanCaseFacts.task(plan, "event:demolish_stray:demolish_stray");
        assertEquals(
                BoardCase.DISMANTLE_SHELTER,
                demolition.boardCase(),
                "Лишний забор сносится кейсом сноса, а не разбирается ради чужой стройки");
        assertTrue(
                PlanCaseFacts.posted(plan, "event:demolish_stray:demolish_stray"),
                "Лишняя постройка без владельца всё равно попадает на доску сноса: " + plan.tasks());
    }
}
