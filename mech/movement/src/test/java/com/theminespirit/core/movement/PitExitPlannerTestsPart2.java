package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class PitExitPlannerTestsPart2 {
    private static final BlockCoord FEET = PitTestWorld.FEET;

    @Test
    void plan_PitUnderPlanCeiling_ChoosesStairsNotPillar() {
        PitTestWorld world = PitTestWorld.pit(4, 0, BlockKind.SOFT);
        BlockCoord ceiling = new BlockCoord(FEET.x(), FEET.y() + 2, FEET.z());
        world.set(ceiling, BlockKind.HARD);

        PitExitPlanner.Plan plan = PitExitPlanner.plan(FEET, world, new PitExitPlanner.Supplies(3, false));

        assertEquals(
                PitExitPlanner.Method.STAIRS,
                plan.method(),
                "Над головой пол постройки плана (" + ceiling + "): столб упрётся в потолок и даст нулевой прогресс "
                        + "(observe25 B2: pillar_up 0 за 3600 тиков). Нужна лестница, а не pillar_up"
                        + " Иначе тест plan_PitUnderPlanCeiling_ChoosesStairsNotPillar перестанет ловить эту поломку. При смене plan_PitUnderPlanCeiling_ChoosesStairsNotPillar проверь это утверждение.");
        assertTrue(
                plan.possible(),
                "Потолок над ямой не отменяет выход: дух обязан выбраться лестницей"
                        + " Иначе тест plan_PitUnderPlanCeiling_ChoosesStairsNotPillar перестанет ловить эту поломку. При смене plan_PitUnderPlanCeiling_ChoosesStairsNotPillar проверь это утверждение.");
    }

    @Test
    void plan_PitInsideBuildingPlan_LeavesRestoreList() {
        PitExitPlanner.Plan plan =
                PitExitPlanner.plan(FEET, PitTestWorld.indoorPit(), new PitExitPlanner.Supplies(0, false));

        assertFalse(
                plan.restore().isEmpty(),
                "Яма внутри плана постройки: за духом остаётся список клеток на засыпку, а не дыра в полу"
                        + " Иначе тест plan_PitInsideBuildingPlan_LeavesRestoreList перестанет ловить эту поломку. При смене plan_PitInsideBuildingPlan_LeavesRestoreList проверь это утверждение.");
    }

    @Test
    void escapeRestore_BodyCells_SkippedOthersReturned() {
        assertTrue(
                EscapeRestore.blockedByBody(FEET, FEET),
                "Клетку под ногами нельзя закладывать обратно: дух замурует себя при возврате разбора"
                        + " Иначе тест escapeRestore_BodyCells_SkippedOthersReturned перестанет ловить эту поломку. При смене escapeRestore_BodyCells_SkippedOthersReturned проверь это утверждение.");
        assertTrue(
                EscapeRestore.blockedByBody(new BlockCoord(FEET.x(), FEET.y() + 1, FEET.z()), FEET),
                "Клетку головы нельзя закладывать обратно: возврат блока задохнул бы духа"
                        + " Иначе тест escapeRestore_BodyCells_SkippedOthersReturned перестанет ловить эту поломку. При смене escapeRestore_BodyCells_SkippedOthersReturned проверь это утверждение.");
        assertFalse(
                EscapeRestore.blockedByBody(new BlockCoord(FEET.x(), FEET.y() + 2, FEET.z()), FEET),
                "Клетку над головой возвращают: после выхода она уже за спиной, и разбор не оставляет следа"
                        + " Иначе тест escapeRestore_BodyCells_SkippedOthersReturned перестанет ловить эту поломку. При смене escapeRestore_BodyCells_SkippedOthersReturned проверь это утверждение.");
        assertFalse(
                EscapeRestore.blockedByBody(new BlockCoord(FEET.x() + 1, FEET.y(), FEET.z()), FEET),
                "Сломанную клетку в стороне возвращают: она не мешает стоять и не оставляет дыры"
                        + " Иначе тест escapeRestore_BodyCells_SkippedOthersReturned перестанет ловить эту поломку. При смене escapeRestore_BodyCells_SkippedOthersReturned проверь это утверждение.");
    }

    @Test
    void plan_LavaAround_RefusesWithoutBlocks() {
        PitTestWorld world = PitTestWorld.pit(4, 0, BlockKind.LAVA);

        PitExitPlanner.Plan plan = PitExitPlanner.plan(FEET, world, new PitExitPlanner.Supplies(0, false));

        assertEquals(
                PitExitPlanner.Method.IMPOSSIBLE,
                plan.method(),
                "Лава вокруг без блоков: лезть в неё нельзя, нужен помощник сверху"
                        + " Иначе тест plan_LavaAround_RefusesWithoutBlocks перестанет ловить эту поломку. При смене plan_LavaAround_RefusesWithoutBlocks проверь это утверждение.");
    }
}
