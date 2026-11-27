package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossActions.scripts;
import static com.theminespirit.core.board.graph.CrossActions.supplyTarget;
import static com.theminespirit.core.board.graph.CrossScene.damaged;
import static com.theminespirit.core.board.graph.CrossScene.facts;
import static com.theminespirit.core.board.graph.CrossScene.ids;
import static com.theminespirit.core.board.graph.CrossScene.named;
import static com.theminespirit.core.board.graph.CrossScene.plan;
import static com.theminespirit.core.board.graph.CrossScene.spirit;
import static com.theminespirit.core.board.graph.CrossScene.stageTwo;
import static com.theminespirit.core.board.graph.CrossScene.task;
import static com.theminespirit.core.board.graph.CrossScene.threat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CrossFarmSupplyScenarioTests {
    @Test
    void husbandry_ShearingAndWornShears_RepairAndFenceStayPosted() {
        GraphWorld world = stageTwo();
        world.built("pen_sheep", Built.BUILT).built("anvil", Built.BUILT);
        world.with("herd_sheep", 6)
                .with("white_wool", 0)
                .with("wheat", 0)
                .with("wheat_seeds", 0)
                .with("iron_ingot", 8);
        world.event("animal_starving");
        GraphFacts facts = new GraphFacts(
                named(
                        spirit(new CrossScene.SpiritSpec(
                                "spirit-1", 20, 20, false, false, null, List.of(wornShears()), List.of())),
                        spirit("spirit-2")),
                List.of(threat("wolf-a", "animal-1", GraphFacts.TargetKind.ANIMAL, 2)),
                List.of(),
                List.of(),
                Map.of(),
                Map.of("fence", damaged("fence", 40, 3, Map.of("oak_fence", 5))),
                List.of(),
                GraphSiteFacts.Terrain.empty(),
                new GraphSiteFacts.StageReqs(Map.of(), Map.of()),
                GraphFeedback.none(),
                List.of(),
                List.of(),
                List.of());
        GraphPlan plan = plan(world, facts);

        GraphPlan.Task shear = task(plan, "shear");
        GraphPlan.Task repairTool = task(plan, "event:tool_worn:repair_tool");
        GraphPlan.Task repairFence = task(plan, "event:repair_fence:repair:fence");
        GraphPlan.Task defend = task(plan, "event:life_threat:defend");

        assertTrue(
                shear.feasible(),
                "Овцы в овчарне — стрижка выполнима:  Иначе тест husbandry_ShearingAndWornShears_RepairAndFenceStayPosted перестанет ловить эту поломку. При смене husbandry_ShearingAndWornShears_RepairAndFenceStayPosted проверь это утверждение."
                        + ids(plan));
        assertTrue(
                repairTool.feasible(),
                "Наковальня и слиток — изношенные ножницы чинятся:  Иначе тест husbandry_ShearingAndWornShears_RepairAndFenceStayPosted перестанет ловить эту поломку. При смене husbandry_ShearingAndWornShears_RepairAndFenceStayPosted проверь это утверждение."
                        + ids(plan));
        assertTrue(
                repairFence.feasible(),
                "Побитый забор чинится рядом со стрижкой и ремонтом:  Иначе тест husbandry_ShearingAndWornShears_RepairAndFenceStayPosted перестанет ловить эту поломку. При смене husbandry_ShearingAndWornShears_RepairAndFenceStayPosted проверь это утверждение."
                        + ids(plan));
        assertTrue(
                defend.feasible(),
                "Волк у стада поднимает защиту:  Иначе тест husbandry_ShearingAndWornShears_RepairAndFenceStayPosted перестанет ловить эту поломку. При смене husbandry_ShearingAndWornShears_RepairAndFenceStayPosted проверь это утверждение."
                        + ids(plan));
        assertEquals(
                BoardCase.ACTIVITY_DEFEND,
                defend.boardCase(),
                "Защита стада — бой, а стрижка остаётся работой:  Иначе тест husbandry_ShearingAndWornShears_RepairAndFenceStayPosted перестанет ловить эту поломку. При смене husbandry_ShearingAndWornShears_RepairAndFenceStayPosted проверь это утверждение."
                        + ids(plan));
        assertEquals(
                BoardCase.ACTIVITY_FARM,
                BoardActivities.caseOfScript("shear").orElseThrow(),
                "Стрижка овец — занятие фермы, а не бой Иначе тест husbandry_ShearingAndWornShears_RepairAndFenceStayPosted перестанет ловить эту поломку. При смене husbandry_ShearingAndWornShears_RepairAndFenceStayPosted проверь это утверждение.");
        assertTrue(
                scripts(BoardCase.REPAIR_TOOL).contains("repair_tool"),
                "Инструмент чинится действием repair_tool:  Иначе тест husbandry_ShearingAndWornShears_RepairAndFenceStayPosted перестанет ловить эту поломку. При смене husbandry_ShearingAndWornShears_RepairAndFenceStayPosted проверь это утверждение."
                        + scripts(BoardCase.REPAIR_TOOL));
        assertTrue(
                scripts(BoardCase.SHEAR_SHEEP, supplyTarget("white_wool")).contains("shear"),
                "Стрижка идёт скриптом shear: "
                        + scripts(
                                BoardCase.SHEAR_SHEEP,
                                supplyTarget(
                                        "white_wool Иначе тест husbandry_ShearingAndWornShears_RepairAndFenceStayPosted перестанет ловить эту поломку. При смене husbandry_ShearingAndWornShears_RepairAndFenceStayPosted проверь это утверждение.")));
        assertTrue(
                scripts(BoardCase.BUILD_FENCE).contains("build_schematic"),
                "Загон и забор строятся схемой build_schematic:  Иначе тест husbandry_ShearingAndWornShears_RepairAndFenceStayPosted перестанет ловить эту поломку. При смене husbandry_ShearingAndWornShears_RepairAndFenceStayPosted проверь это утверждение."
                        + scripts(BoardCase.BUILD_FENCE));
    }

    private static GraphFacts.Tool wornShears() {
        return new GraphFacts.Tool("minecraft:shears", "shears", 0, 1, 100);
    }
}
