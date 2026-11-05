package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardCase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CleanupCasesTests {
    @Test
    void cleanup_StrayFence_PostsDemolition() {
        GraphFacts facts = PlanCaseFacts.facts(
                Map.of(),
                List.of(new GraphSiteFacts.Stray(
                        "stray:oak_fence@1,69,1", "oak_fence", new GraphFacts.Position(1, 69, 1), "spirit-1")),
                GraphSiteFacts.Terrain.empty());

        GraphPlan plan = PlanCaseFacts.plan(GraphWorld.settled(), facts);

        GraphPlan.Task demolition = PlanCaseFacts.task(plan, "event:demolish_stray:demolish_stray");
        assertTrue(demolition.feasible(), "Лишний забор вне плана обязан получить задание сноса: " + plan.tasks());
        assertEquals(
                BoardCase.DISMANTLE_SHELTER,
                demolition.boardCase(),
                "Снос лишнего идёт кейсом уборки, иначе задание не находит исполнителя");
    }

    @Test
    void cleanup_Stump_PostsFloatingCleanup() {
        GraphFacts facts = PlanCaseFacts.facts(
                Map.of(),
                List.of(),
                new GraphSiteFacts.Terrain(0, 1, 0, 0, 0, Map.of(), Map.of(), Map.of("minecraft:oak_log", 1)));

        GraphPlan plan = PlanCaseFacts.plan(GraphWorld.settled(), facts);

        GraphPlan.Task cleanup = PlanCaseFacts.task(plan, "event:clear_floating:clear_floating");
        assertTrue(cleanup.feasible(), "Пень — бревно без дерева — убирается заданием висящего: " + plan.tasks());
        assertEquals(
                BoardCase.CLEAR_GROUNDS,
                cleanup.boardCase(),
                "Уборка пня идёт кейсом расчистки, а не отдельной выдуманной работой");
    }

    @Test
    void cleanup_HangingCrown_PostsFloatingCleanup() {
        GraphFacts facts = PlanCaseFacts.facts(
                Map.of(),
                List.of(),
                new GraphSiteFacts.Terrain(0, 6, 0, 0, 0, Map.of(), Map.of(), Map.of("minecraft:oak_leaves", 6)));

        GraphPlan plan = PlanCaseFacts.plan(GraphWorld.settled(), facts);

        assertTrue(
                PlanCaseFacts.posted(plan, "event:clear_floating:clear_floating"),
                "Висящая крона после рубки ставит уборку, а не остаётся висеть навсегда: " + plan.tasks());
    }

    @Test
    void cleanup_PillarTrace_PostsRestoreTrace() {
        GraphFacts facts = PlanCaseFacts.facts(Map.of(), List.of(), new GraphSiteFacts.Terrain(0, 0, 0, 0, 3));

        GraphPlan plan = PlanCaseFacts.plan(GraphWorld.settled(), facts);

        GraphPlan.Task restore = PlanCaseFacts.task(plan, "event:restore_trace:restore_trace");
        assertTrue(restore.feasible(), "Следы pillar/bridge возвращаются заданием restore_trace: " + plan.tasks());
        assertEquals(
                BoardCase.FILL_CRATER,
                restore.boardCase(),
                "Возврат следа идёт засыпкой/ремонтом кейса FILL_CRATER, а не новой работой");
    }

    @Test
    void cleanup_PitInSettlement_PostsBackfill() {
        GraphWorld world = GraphWorld.settled().with("dirt", 64);
        GraphFacts facts = PlanCaseFacts.facts(
                Map.of(),
                List.of(),
                new GraphSiteFacts.Terrain(2, 0, 0, 2, 0, Map.of(), Map.of("minecraft:dirt", 3), Map.of()));

        GraphPlan plan = PlanCaseFacts.plan(world, facts);

        GraphPlan.Task backfill = PlanCaseFacts.task(plan, "event:backfill:backfill");
        assertTrue(backfill.feasible(), "Яма от духа на участке засыпается при запасе земли: " + plan.tasks());
        assertEquals(
                BoardCase.FILL_CRATER,
                backfill.boardCase(),
                "Засыпка ямы идёт кейсом FILL_CRATER: иначе землю некому носить");
        assertEquals(Map.of("dirt", 4), backfill.consumes(), "Засыпка просит землю, а не любой материал");
    }

    @Test
    void cleanup_NaturalSlopeOnly_DoesNoLeveling() {
        GraphFacts facts = PlanCaseFacts.facts(
                Map.of(),
                List.of(),
                new GraphSiteFacts.Terrain(0, 0, 0, 0, 0, Map.of(), Map.of(), Map.of(), false, 3264));

        GraphPlan plan = PlanCaseFacts.plan(GraphWorld.settled(), facts);

        assertFalse(
                PlanCaseFacts.posted(plan, "event:level_lot:level_lot"),
                "Холм вне участка — природный рельеф, а не работа: его не срезают: " + plan.tasks());
        assertFalse(
                PlanCaseFacts.posted(plan, "event:backfill:backfill"),
                "Природная впадина без ям и подсыпки не превращается в засыпку: " + plan.tasks());
    }

    @Test
    void cleanup_PitsAndMoundsOnLot_PostsBothWorks() {
        GraphWorld world = GraphWorld.settled().with("dirt", 64);
        GraphFacts facts = PlanCaseFacts.facts(
                Map.of(),
                List.of(),
                new GraphSiteFacts.Terrain(
                        2, 0, 3, 2, 0, Map.of("minecraft:dirt", 4), Map.of("minecraft:dirt", 2), Map.of()));

        GraphPlan plan = PlanCaseFacts.plan(world, facts);

        assertTrue(
                PlanCaseFacts.posted(plan, "event:backfill:backfill"),
                "Ямы на лоте засыпаются до стройки: " + plan.tasks());
        assertTrue(
                PlanCaseFacts.posted(plan, "event:level_lot:level_lot"),
                "Насыпь на лоте срезается до стройки: " + plan.tasks());
    }

    @Test
    void cleanup_FillDirtInStock_DoesNotDigAgain() {
        GraphWorld world = GraphWorld.settled().with("dirt", 64);
        GraphFacts facts = PlanCaseFacts.facts(
                Map.of(),
                List.of(),
                new GraphSiteFacts.Terrain(2, 0, 0, 2, 0, Map.of(), Map.of("minecraft:dirt", 2), Map.of()));

        GraphPlan plan = PlanCaseFacts.plan(world, facts);

        assertTrue(
                PlanCaseFacts.posted(plan, "event:backfill:backfill"),
                "Земля есть на складе — засыпка выполнима без новой добычи: " + plan.tasks());
        assertTrue(
                plan.tasks().stream().noneMatch(task -> task.feasible() && "dirt".equals(task.makes())),
                "Земли в сундуках хватает — добыча земли не ставится, иначе сундуки пополняются землёй "
                        + "вместо расхода: " + plan.tasks());
    }
}
