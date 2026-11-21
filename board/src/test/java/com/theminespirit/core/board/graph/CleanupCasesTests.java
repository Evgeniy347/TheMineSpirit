package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
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
        assertTrue(
                demolition.feasible(),
                "Лишний забор вне плана обязан получить задание сноса: " + plan.tasks()
                        + " Иначе тест cleanup_StrayFence_PostsDemolition перестанет ловить эту поломку. При смене cleanup_StrayFence_PostsDemolition проверь это утверждение.");
        assertEquals(
                BoardCase.DISMANTLE_SHELTER,
                demolition.boardCase(),
                "Снос лишнего идёт кейсом уборки, иначе задание не находит исполнителя"
                        + " При смене cleanup_StrayFence_PostsDemolition проверь это утверждение.");
    }

    @Test
    void cleanup_Stump_PostsFloatingCleanup() {
        GraphFacts facts = PlanCaseFacts.facts(
                Map.of(),
                List.of(),
                new GraphSiteFacts.Terrain(0, 1, 0, 0, Map.of(), Map.of(), Map.of("minecraft:oak_log", 1)));

        GraphPlan plan = PlanCaseFacts.plan(GraphWorld.settled(), facts);

        GraphPlan.Task cleanup = PlanCaseFacts.task(plan, "event:clear_floating:clear_floating");
        assertTrue(
                cleanup.feasible(),
                "Пень — бревно без дерева — убирается заданием висящего: " + plan.tasks()
                        + " Иначе тест cleanup_Stump_PostsFloatingCleanup перестанет ловить эту поломку. При смене cleanup_Stump_PostsFloatingCleanup проверь это утверждение.");
        assertEquals(
                BoardCase.CLEAR_GROUNDS,
                cleanup.boardCase(),
                "Уборка пня идёт кейсом расчистки, а не отдельной выдуманной работой"
                        + " Иначе тест cleanup_Stump_PostsFloatingCleanup перестанет ловить эту поломку. При смене cleanup_Stump_PostsFloatingCleanup проверь это утверждение.");
    }

    @Test
    void cleanup_HangingCrown_PostsFloatingCleanup() {
        GraphFacts facts = PlanCaseFacts.facts(
                Map.of(),
                List.of(),
                new GraphSiteFacts.Terrain(0, 6, 0, 0, Map.of(), Map.of(), Map.of("minecraft:oak_leaves", 6)));

        GraphPlan plan = PlanCaseFacts.plan(GraphWorld.settled(), facts);

        assertTrue(
                PlanCaseFacts.posted(plan, "event:clear_floating:clear_floating"),
                "Висящая крона после рубки ставит уборку, а не остаётся висеть навсегда: " + plan.tasks()
                        + " Иначе тест cleanup_HangingCrown_PostsFloatingCleanup перестанет ловить эту поломку. При смене cleanup_HangingCrown_PostsFloatingCleanup проверь это утверждение.");
    }

    @Test
    void cleanup_PitInSettlement_PostsBackfill() {
        GraphWorld world = GraphWorld.settled().with("dirt", 64);
        GraphFacts facts = PlanCaseFacts.facts(
                Map.of(),
                List.of(),
                new GraphSiteFacts.Terrain(2, 0, 0, 2, Map.of(), Map.of("minecraft:dirt", 3), Map.of()));

        GraphPlan plan = PlanCaseFacts.plan(world, facts);

        GraphPlan.Task backfill = PlanCaseFacts.task(plan, "event:backfill:backfill");
        assertTrue(
                backfill.feasible(),
                "Яма от духа на участке засыпается при запасе земли: " + plan.tasks()
                        + " Иначе тест cleanup_PitInSettlement_PostsBackfill перестанет ловить эту поломку. При смене cleanup_PitInSettlement_PostsBackfill проверь это утверждение.");
        assertEquals(
                BoardCase.FILL_CRATER,
                backfill.boardCase(),
                "Засыпка ямы идёт кейсом FILL_CRATER: иначе землю некому носить"
                        + " При смене cleanup_PitInSettlement_PostsBackfill проверь это утверждение.");
        assertEquals(
                Map.of("dirt", 4),
                backfill.consumes(),
                "Засыпка просит землю, а не любой материал"
                        + " Иначе тест cleanup_PitInSettlement_PostsBackfill перестанет ловить эту поломку. При смене cleanup_PitInSettlement_PostsBackfill проверь это утверждение.");
    }

    @Test
    void cleanup_NoDirtForMound_LevelLotWaitsForMaterial() {
        GraphWorld world = GraphWorld.settled().with("dirt", 0);
        GraphFacts facts = PlanCaseFacts.facts(
                Map.of(),
                List.of(),
                new GraphSiteFacts.Terrain(0, 0, 2, 0, Map.of("minecraft:dirt", 2), Map.of(), Map.of(), false));

        GraphPlan plan = PlanCaseFacts.plan(world, facts);

        assertFalse(
                PlanCaseFacts.task(plan, "event:level_lot:level_lot").feasible(),
                "Выравнивание без земли не крутится: узел ждёт материал, а не падает по кругу: " + plan.tasks()
                        + " Иначе тест cleanup_NoDirtForMound_LevelLotWaitsForMaterial перестанет ловить эту поломку. При смене cleanup_NoDirtForMound_LevelLotWaitsForMaterial проверь это утверждение.");
    }

    @Test
    void cleanup_NaturalSlopeOnly_DoesNoLeveling() {
        GraphFacts facts = PlanCaseFacts.facts(
                Map.of(), List.of(), new GraphSiteFacts.Terrain(0, 0, 0, 0, Map.of(), Map.of(), Map.of(), false, 3264));

        GraphPlan plan = PlanCaseFacts.plan(GraphWorld.settled(), facts);

        assertFalse(
                PlanCaseFacts.posted(plan, "event:level_lot:level_lot"),
                "Холм вне участка — природный рельеф, а не работа: его не срезают: " + plan.tasks()
                        + " Иначе тест cleanup_NaturalSlopeOnly_DoesNoLeveling перестанет ловить эту поломку. При смене cleanup_NaturalSlopeOnly_DoesNoLeveling проверь это утверждение.");
        assertFalse(
                PlanCaseFacts.posted(plan, "event:backfill:backfill"),
                "Природная впадина без ям и подсыпки не превращается в засыпку: " + plan.tasks()
                        + " Иначе тест cleanup_NaturalSlopeOnly_DoesNoLeveling перестанет ловить эту поломку. При смене cleanup_NaturalSlopeOnly_DoesNoLeveling проверь это утверждение.");
    }

    @Test
    void cleanup_PitsAndMoundsOnLot_PostsBothWorks() {
        GraphWorld world = GraphWorld.settled().with("dirt", 64);
        GraphFacts facts = PlanCaseFacts.facts(
                Map.of(),
                List.of(),
                new GraphSiteFacts.Terrain(
                        2, 0, 3, 2, Map.of("minecraft:dirt", 4), Map.of("minecraft:dirt", 2), Map.of()));

        GraphPlan plan = PlanCaseFacts.plan(world, facts);

        assertTrue(
                PlanCaseFacts.posted(plan, "event:backfill:backfill"),
                "Ямы на лоте засыпаются до стройки: " + plan.tasks()
                        + " Иначе тест cleanup_PitsAndMoundsOnLot_PostsBothWorks перестанет ловить эту поломку. При смене cleanup_PitsAndMoundsOnLot_PostsBothWorks проверь это утверждение.");
        assertTrue(
                PlanCaseFacts.posted(plan, "event:level_lot:level_lot"),
                "Насыпь на лоте срезается до стройки: " + plan.tasks()
                        + " Иначе тест cleanup_PitsAndMoundsOnLot_PostsBothWorks перестанет ловить эту поломку. При смене cleanup_PitsAndMoundsOnLot_PostsBothWorks проверь это утверждение.");
    }

    @Test
    void cleanup_FillDirtInStock_DoesNotDigAgain() {
        GraphWorld world = GraphWorld.settled().with("dirt", 64);
        GraphFacts facts = PlanCaseFacts.facts(
                Map.of(),
                List.of(),
                new GraphSiteFacts.Terrain(2, 0, 0, 2, Map.of(), Map.of("minecraft:dirt", 2), Map.of()));

        GraphPlan plan = PlanCaseFacts.plan(world, facts);

        assertTrue(
                PlanCaseFacts.posted(plan, "event:backfill:backfill"),
                "Земля есть на складе — засыпка выполнима без новой добычи: " + plan.tasks()
                        + " Иначе тест cleanup_FillDirtInStock_DoesNotDigAgain перестанет ловить эту поломку. При смене cleanup_FillDirtInStock_DoesNotDigAgain проверь это утверждение.");
        assertTrue(
                plan.tasks().stream().noneMatch(task -> task.feasible() && "dirt".equals(task.makes())),
                "Земли в сундуках хватает — добыча земли не ставится, иначе сундуки пополняются землёй "
                        + "вместо расхода: " + plan.tasks()
                        + " При смене cleanup_FillDirtInStock_DoesNotDigAgain проверь это утверждение.");
    }
}
