package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class PitExitPlannerTests {
    private static final BlockCoord FEET = PitTestWorld.FEET;

    @Test
    void plan_FlatGround_WalksWithoutBreaking() {
        PitExitPlanner.Plan plan =
                PitExitPlanner.plan(FEET, PitTestWorld.flat(), new PitExitPlanner.Supplies(0, false));

        assertEquals(
                PitExitPlanner.Method.WALK,
                plan.method(),
                "На ровном месте ямы нет: дух идёт шагом, а не копает и не ставит столб");
        assertTrue(plan.dig().isEmpty(), "Ровная земля не требует ломать блоки");
    }

    @Test
    void plan_PitThreeWithBlocks_PlacesTwoSteps() {
        PitExitPlanner.Plan plan = PitExitPlanner.plan(
                FEET, PitTestWorld.pit(3, 0, PitExitPlanner.Kind.HARD), new PitExitPlanner.Supplies(2, false));

        assertEquals(PitExitPlanner.Method.PILLAR, plan.method(), "С двумя блоками дух поднимается столбом");
        assertEquals(2, plan.rise(), "Из ямы в три блока столб на два — и шаг на край");
        assertEquals(new BlockCoord(1, 67, 0), plan.exit(), "Выход ведёт на ближайший край ямы");
    }

    @Test
    void plan_PitFiveWithBlocks_PlacesFourSteps() {
        PitExitPlanner.Plan plan = PitExitPlanner.plan(
                FEET, PitTestWorld.pit(5, 0, PitExitPlanner.Kind.HARD), new PitExitPlanner.Supplies(4, false));

        assertEquals(PitExitPlanner.Method.PILLAR, plan.method(), "Пятиблочная яма с четырьмя блоками — столбом");
        assertEquals(4, plan.rise(), "Подъём на четыре блока");
    }

    @Test
    void plan_PitTenWithBlocks_PlacesNineSteps() {
        PitExitPlanner.Plan plan = PitExitPlanner.plan(
                FEET, PitTestWorld.pit(10, 0, PitExitPlanner.Kind.HARD), new PitExitPlanner.Supplies(9, false));

        assertEquals(PitExitPlanner.Method.PILLAR, plan.method(), "Десятиблочная яма с девятью блоками — столбом");
        assertEquals(9, plan.rise(), "Подъём на девять блоков");
    }

    @Test
    void plan_PitTwentyWithBlocks_PlacesNineteenSteps() {
        PitExitPlanner.Plan plan = PitExitPlanner.plan(
                FEET, PitTestWorld.pit(20, 0, PitExitPlanner.Kind.HARD), new PitExitPlanner.Supplies(19, false));

        assertEquals(PitExitPlanner.Method.PILLAR, plan.method(), "Двадцать блоков глубины: столб на девятнадцать");
        assertEquals(19, plan.rise(), "Подъём на девятнадцать блоков");
    }

    @Test
    void plan_PitThreeNoBlocksSoftWalls_DigsSoftStairs() {
        PitExitPlanner.Plan plan = PitExitPlanner.plan(
                FEET, PitTestWorld.pit(3, 0, PitExitPlanner.Kind.SOFT), new PitExitPlanner.Supplies(0, false));

        assertEquals(
                PitExitPlanner.Method.STAIRS, plan.method(), "Без блоков и кирки дух режет земляную лестницу руками");
        assertTrue(plan.rise() >= 2, "Лестница поднимает духа к краю ямы");
        assertFalse(plan.dig().isEmpty(), "Лестница в стене требует выломать ступени");
    }

    @Test
    void plan_PitFiveNoBlocksHardWalls_DigsEmergencyStairs() {
        PitExitPlanner.Plan plan = PitExitPlanner.plan(
                FEET, PitTestWorld.pit(5, 0, PitExitPlanner.Kind.HARD), new PitExitPlanner.Supplies(0, false));

        assertEquals(
                PitExitPlanner.Method.STAIRS,
                plan.method(),
                "Раз залез — обязан вылезти: камень без кирки режется аварийно, без добычи, но дух не ждёт спасателя");
        assertTrue(plan.why().contains("аварийно"), "Причина называет аварийную ломку: журнал не выдаёт её за добычу");
    }

    @Test
    void plan_PitThreeWithPickaxe_DigsStairs() {
        PitExitPlanner.Plan plan = PitExitPlanner.plan(
                FEET, PitTestWorld.pit(3, 0, PitExitPlanner.Kind.HARD), new PitExitPlanner.Supplies(0, true));

        assertEquals(PitExitPlanner.Method.STAIRS, plan.method(), "С киркой дух режет каменную лестницу");
        assertFalse(plan.why().contains("аварийно"), "С киркой ломка обычная, а не аварийная");
    }

    @Test
    void plan_PitTenWithPickaxe_DigsStairs() {
        PitExitPlanner.Plan plan = PitExitPlanner.plan(
                FEET, PitTestWorld.pit(10, 0, PitExitPlanner.Kind.HARD), new PitExitPlanner.Supplies(0, true));

        assertEquals(PitExitPlanner.Method.STAIRS, plan.method(), "Десять блоков с киркой — лестница в стене");
        assertTrue(plan.rise() >= 9, "Лестница поднимает на высоту ямы");
    }

    @Test
    void plan_PitTwentyNoBlocksNoTool_DigsEmergencyStairs() {
        PitExitPlanner.Plan plan = PitExitPlanner.plan(
                FEET, PitTestWorld.pit(20, 0, PitExitPlanner.Kind.HARD), new PitExitPlanner.Supplies(0, false));

        assertEquals(
                PitExitPlanner.Method.STAIRS,
                plan.method(),
                "Двадцатиблочная яма без ничего: аварийная лестница вверх");
        assertTrue(plan.rise() >= 19, "Выход не оставляет духа на дне");
    }

    @Test
    void plan_StoneNoToolWithBlocks_ChoosesPillarNotDig() {
        PitExitPlanner.Plan plan = PitExitPlanner.plan(
                FEET, PitTestWorld.pit(4, 0, PitExitPlanner.Kind.HARD), new PitExitPlanner.Supplies(3, false));

        assertEquals(
                PitExitPlanner.Method.PILLAR,
                plan.method(),
                "Камень руками не берётся: при своих блоках дух ставит столб, а не тратит время на аварийную ломку");
    }

    @Test
    void plan_WaterOnBottom_StillFindsDryEscape() {
        PitTestWorld world = PitTestWorld.pit(4, 0, PitExitPlanner.Kind.SOFT);
        world.set(FEET, PitExitPlanner.Kind.WATER);

        PitExitPlanner.Plan plan = PitExitPlanner.plan(FEET, world, new PitExitPlanner.Supplies(0, false));

        assertTrue(plan.possible(), "Вода на дне не отменяет выход: дух всплывает или режет лестницу");
        assertFalse(plan.dig().contains(FEET), "Дух не ломает воду под собой");
    }

    @Test
    void plan_GravelAbove_EscapesWithoutDiggingUnderIt() {
        PitTestWorld world = PitTestWorld.pit(5, 0, PitExitPlanner.Kind.SOFT);
        world.set(new BlockCoord(1, 66, 0), PitExitPlanner.Kind.FALLING);
        world.set(new BlockCoord(1, 67, 0), PitExitPlanner.Kind.FALLING);

        PitExitPlanner.Plan plan = PitExitPlanner.plan(FEET, world, new PitExitPlanner.Supplies(0, false));

        assertTrue(plan.possible(), "Гравий сверху не запирает духа: он выбирает сторону без падающих блоков");
        for (BlockCoord cell : plan.dig()) {
            assertFalse(
                    world.at(PitTestWorld.below(cell)) == PitExitPlanner.Kind.FALLING
                            && !plan.dig().contains(PitTestWorld.below(cell)),
                    "Клетка под падающим гравием не выбивается без самого гравия: иначе он падает на духа");
        }
    }

    @Test
    void plan_PitUnderWarehouse_ExitsAtFloorNotRoof() {
        PitExitPlanner.Plan plan =
                PitExitPlanner.plan(FEET, PitTestWorld.indoorPit(), new PitExitPlanner.Supplies(0, false));

        assertTrue(plan.possible(), "Яма под складом не запирает духа: выход ищется на уровне пола, а не крыши");
        assertEquals(69, plan.exit().y(), "Выход ведёт на пол склада (69), а не на его крышу");
    }

    @Test
    void plan_MineBelowWaterLevel_EscapesUp() {
        PitTestWorld world = PitTestWorld.pit(9, 0, PitExitPlanner.Kind.HARD);
        world.set(new BlockCoord(1, 64, 0), PitExitPlanner.Kind.WATER);
        world.set(new BlockCoord(1, 65, 0), PitExitPlanner.Kind.WATER);

        PitExitPlanner.Plan plan = PitExitPlanner.plan(FEET, world, new PitExitPlanner.Supplies(0, true));

        assertTrue(plan.possible(), "Затопленный нижний горизонт шахты: дух выходит вверх, а не тонет");
    }

    @Test
    void plan_CaveWithoutExit_DigsToSurface() {
        PitTestWorld world = PitTestWorld.pit(12, 2, PitExitPlanner.Kind.HARD);

        PitExitPlanner.Plan plan = PitExitPlanner.plan(FEET, world, new PitExitPlanner.Supplies(0, true));

        assertEquals(
                PitExitPlanner.Method.STAIRS, plan.method(), "Пещера без прохода: дух режет лестницу к поверхности");
        assertFalse(plan.dig().isEmpty(), "Без прохода выход строится ломкой");
    }

    @Test
    void plan_PitUnderPlanCeiling_ChoosesStairsNotPillar() {
        PitTestWorld world = PitTestWorld.pit(4, 0, PitExitPlanner.Kind.SOFT);
        BlockCoord ceiling = new BlockCoord(FEET.x(), FEET.y() + 2, FEET.z());
        world.set(ceiling, PitExitPlanner.Kind.HARD);

        PitExitPlanner.Plan plan = PitExitPlanner.plan(FEET, world, new PitExitPlanner.Supplies(3, false));

        assertEquals(
                PitExitPlanner.Method.STAIRS,
                plan.method(),
                "Над головой пол постройки плана (" + ceiling + "): столб упрётся в потолок и даст нулевой прогресс "
                        + "(observe25 B2: pillar_up 0 за 3600 тиков). Нужна лестница, а не pillar_up");
        assertTrue(plan.possible(), "Потолок над ямой не отменяет выход: дух обязан выбраться лестницей");
    }

    @Test
    void plan_PitInsideBuildingPlan_LeavesRestoreList() {
        PitExitPlanner.Plan plan =
                PitExitPlanner.plan(FEET, PitTestWorld.indoorPit(), new PitExitPlanner.Supplies(0, false));

        assertFalse(
                plan.restore().isEmpty(),
                "Яма внутри плана постройки: за духом остаётся список клеток на засыпку, а не дыра в полу");
    }

    @Test
    void escapeRestore_BodyCells_SkippedOthersReturned() {
        assertTrue(
                EscapeRestore.blockedByBody(FEET, FEET),
                "Клетку под ногами нельзя закладывать обратно: дух замурует себя при возврате разбора");
        assertTrue(
                EscapeRestore.blockedByBody(new BlockCoord(FEET.x(), FEET.y() + 1, FEET.z()), FEET),
                "Клетку головы нельзя закладывать обратно: возврат блока задохнул бы духа");
        assertFalse(
                EscapeRestore.blockedByBody(new BlockCoord(FEET.x(), FEET.y() + 2, FEET.z()), FEET),
                "Клетку над головой возвращают: после выхода она уже за спиной, и разбор не оставляет следа");
        assertFalse(
                EscapeRestore.blockedByBody(new BlockCoord(FEET.x() + 1, FEET.y(), FEET.z()), FEET),
                "Сломанную клетку в стороне возвращают: она не мешает стоять и не оставляет дыры");
    }

    @Test
    void plan_LavaAround_RefusesWithoutBlocks() {
        PitTestWorld world = PitTestWorld.pit(4, 0, PitExitPlanner.Kind.LAVA);

        PitExitPlanner.Plan plan = PitExitPlanner.plan(FEET, world, new PitExitPlanner.Supplies(0, false));

        assertEquals(
                PitExitPlanner.Method.IMPOSSIBLE,
                plan.method(),
                "Лава вокруг без блоков: лезть в неё нельзя, нужен помощник сверху");
    }
}
