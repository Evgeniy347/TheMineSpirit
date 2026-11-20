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
                "На ровном месте ямы нет: дух идёт шагом, а не копает и не ставит столб"
                        + " Иначе тест plan_FlatGround_WalksWithoutBreaking перестанет ловить эту поломку. При смене plan_FlatGround_WalksWithoutBreaking проверь это утверждение.");
        assertTrue(
                plan.dig().isEmpty(),
                "Ровная земля не требует ломать блоки"
                        + " Иначе тест plan_FlatGround_WalksWithoutBreaking перестанет ловить эту поломку. При смене plan_FlatGround_WalksWithoutBreaking проверь это утверждение.");
    }

    @Test
    void plan_ChestAboveHeadInTwoDeepPit_DoesNotChoosePillar() {
        PitTestWorld world = PitTestWorld.pit(2, 0, BlockKind.SOFT);
        world.set(new BlockCoord(FEET.x(), FEET.y() + 2, FEET.z()), BlockKind.HARD);

        PitExitPlanner.Plan plan = PitExitPlanner.plan(FEET, world, new PitExitPlanner.Supplies(8, false));

        assertTrue(
                plan.method() != PitExitPlanner.Method.PILLAR,
                "столб упирает голову в сундук над ямой, дух прыгает на месте вечно (spirit-5, -181,67,-518)"
                        + " Иначе тест plan_ChestAboveHeadInTwoDeepPit_DoesNotChoosePillar перестанет ловить эту поломку. При смене plan_ChestAboveHeadInTwoDeepPit_DoesNotChoosePillar проверь это утверждение.");
    }

    @Test
    void plan_PitThreeWithBlocks_PlacesTwoSteps() {
        PitExitPlanner.Plan plan = PitExitPlanner.plan(
                FEET, PitTestWorld.pit(3, 0, BlockKind.HARD), new PitExitPlanner.Supplies(2, false));

        assertEquals(
                PitExitPlanner.Method.PILLAR,
                plan.method(),
                "С двумя блоками дух поднимается столбом"
                        + " Иначе тест plan_PitThreeWithBlocks_PlacesTwoSteps перестанет ловить эту поломку. При смене plan_PitThreeWithBlocks_PlacesTwoSteps проверь это утверждение.");
        assertEquals(
                2,
                plan.rise(),
                "Из ямы в три блока столб на два — и шаг на край"
                        + " Иначе тест plan_PitThreeWithBlocks_PlacesTwoSteps перестанет ловить эту поломку. При смене plan_PitThreeWithBlocks_PlacesTwoSteps проверь это утверждение.");
        assertEquals(
                new BlockCoord(1, 67, 0),
                plan.exit(),
                "Выход ведёт на ближайший край ямы"
                        + " Иначе тест plan_PitThreeWithBlocks_PlacesTwoSteps перестанет ловить эту поломку. При смене plan_PitThreeWithBlocks_PlacesTwoSteps проверь это утверждение.");
    }

    @Test
    void plan_PitFiveWithBlocks_PlacesFourSteps() {
        PitExitPlanner.Plan plan = PitExitPlanner.plan(
                FEET, PitTestWorld.pit(5, 0, BlockKind.HARD), new PitExitPlanner.Supplies(4, false));

        assertEquals(
                PitExitPlanner.Method.PILLAR,
                plan.method(),
                "Пятиблочная яма с четырьмя блоками — столбом"
                        + " Иначе тест plan_PitFiveWithBlocks_PlacesFourSteps перестанет ловить эту поломку. При смене plan_PitFiveWithBlocks_PlacesFourSteps проверь это утверждение.");
        assertEquals(
                4,
                plan.rise(),
                "Подъём на четыре блока"
                        + " Иначе тест plan_PitFiveWithBlocks_PlacesFourSteps перестанет ловить эту поломку. При смене plan_PitFiveWithBlocks_PlacesFourSteps проверь это утверждение.");
    }

    @Test
    void plan_PitTenWithBlocks_PlacesNineSteps() {
        PitExitPlanner.Plan plan = PitExitPlanner.plan(
                FEET, PitTestWorld.pit(10, 0, BlockKind.HARD), new PitExitPlanner.Supplies(9, false));

        assertEquals(
                PitExitPlanner.Method.PILLAR,
                plan.method(),
                "Десятиблочная яма с девятью блоками — столбом"
                        + " Иначе тест plan_PitTenWithBlocks_PlacesNineSteps перестанет ловить эту поломку. При смене plan_PitTenWithBlocks_PlacesNineSteps проверь это утверждение.");
        assertEquals(
                9,
                plan.rise(),
                "Подъём на девять блоков"
                        + " Иначе тест plan_PitTenWithBlocks_PlacesNineSteps перестанет ловить эту поломку. При смене plan_PitTenWithBlocks_PlacesNineSteps проверь это утверждение.");
    }

    @Test
    void plan_PitTwentyWithBlocks_PlacesNineteenSteps() {
        PitExitPlanner.Plan plan = PitExitPlanner.plan(
                FEET, PitTestWorld.pit(20, 0, BlockKind.HARD), new PitExitPlanner.Supplies(19, false));

        assertEquals(
                PitExitPlanner.Method.PILLAR,
                plan.method(),
                "Двадцать блоков глубины: столб на девятнадцать"
                        + " Иначе тест plan_PitTwentyWithBlocks_PlacesNineteenSteps перестанет ловить эту поломку. При смене plan_PitTwentyWithBlocks_PlacesNineteenSteps проверь это утверждение.");
        assertEquals(
                19,
                plan.rise(),
                "Подъём на девятнадцать блоков"
                        + " Иначе тест plan_PitTwentyWithBlocks_PlacesNineteenSteps перестанет ловить эту поломку. При смене plan_PitTwentyWithBlocks_PlacesNineteenSteps проверь это утверждение.");
    }

    @Test
    void plan_PitThreeNoBlocksSoftWalls_DigsSoftStairs() {
        PitExitPlanner.Plan plan = PitExitPlanner.plan(
                FEET, PitTestWorld.pit(3, 0, BlockKind.SOFT), new PitExitPlanner.Supplies(0, false));

        assertEquals(
                PitExitPlanner.Method.STAIRS,
                plan.method(),
                "Без блоков и кирки дух режет земляную лестницу руками"
                        + " Иначе тест plan_PitThreeNoBlocksSoftWalls_DigsSoftStairs перестанет ловить эту поломку. При смене plan_PitThreeNoBlocksSoftWalls_DigsSoftStairs проверь это утверждение.");
        assertTrue(
                plan.rise() >= 2,
                "Лестница поднимает духа к краю ямы"
                        + " Иначе тест plan_PitThreeNoBlocksSoftWalls_DigsSoftStairs перестанет ловить эту поломку. При смене plan_PitThreeNoBlocksSoftWalls_DigsSoftStairs проверь это утверждение.");
        assertFalse(
                plan.dig().isEmpty(),
                "Лестница в стене требует выломать ступени"
                        + " Иначе тест plan_PitThreeNoBlocksSoftWalls_DigsSoftStairs перестанет ловить эту поломку. При смене plan_PitThreeNoBlocksSoftWalls_DigsSoftStairs проверь это утверждение.");
    }

    @Test
    void plan_PitFiveNoBlocksHardWalls_DigsEmergencyStairs() {
        PitExitPlanner.Plan plan = PitExitPlanner.plan(
                FEET, PitTestWorld.pit(5, 0, BlockKind.HARD), new PitExitPlanner.Supplies(0, false));

        assertEquals(
                PitExitPlanner.Method.STAIRS,
                plan.method(),
                "Раз залез — обязан вылезти: камень без кирки режется аварийно, без добычи, но дух не ждёт спасателя"
                        + " Иначе тест plan_PitFiveNoBlocksHardWalls_DigsEmergencyStairs перестанет ловить эту поломку. При смене plan_PitFiveNoBlocksHardWalls_DigsEmergencyStairs проверь это утверждение.");
        assertTrue(
                plan.why().contains("аварийно"),
                "Причина называет аварийную ломку: журнал не выдаёт её за добычу"
                        + " Иначе тест plan_PitFiveNoBlocksHardWalls_DigsEmergencyStairs перестанет ловить эту поломку. При смене plan_PitFiveNoBlocksHardWalls_DigsEmergencyStairs проверь это утверждение.");
    }

    @Test
    void plan_PitThreeWithPickaxe_DigsStairs() {
        PitExitPlanner.Plan plan =
                PitExitPlanner.plan(FEET, PitTestWorld.pit(3, 0, BlockKind.HARD), new PitExitPlanner.Supplies(0, true));

        assertEquals(
                PitExitPlanner.Method.STAIRS,
                plan.method(),
                "С киркой дух режет каменную лестницу"
                        + " Иначе тест plan_PitThreeWithPickaxe_DigsStairs перестанет ловить эту поломку. При смене plan_PitThreeWithPickaxe_DigsStairs проверь это утверждение.");
        assertFalse(
                plan.why().contains("аварийно"),
                "С киркой ломка обычная, а не аварийная"
                        + " Иначе тест plan_PitThreeWithPickaxe_DigsStairs перестанет ловить эту поломку. При смене plan_PitThreeWithPickaxe_DigsStairs проверь это утверждение.");
    }

    @Test
    void plan_PitTenWithPickaxe_DigsStairs() {
        PitExitPlanner.Plan plan = PitExitPlanner.plan(
                FEET, PitTestWorld.pit(10, 0, BlockKind.HARD), new PitExitPlanner.Supplies(0, true));

        assertEquals(
                PitExitPlanner.Method.STAIRS,
                plan.method(),
                "Десять блоков с киркой — лестница в стене"
                        + " Иначе тест plan_PitTenWithPickaxe_DigsStairs перестанет ловить эту поломку. При смене plan_PitTenWithPickaxe_DigsStairs проверь это утверждение.");
        assertTrue(
                plan.rise() >= 9,
                "Лестница поднимает на высоту ямы"
                        + " Иначе тест plan_PitTenWithPickaxe_DigsStairs перестанет ловить эту поломку. При смене plan_PitTenWithPickaxe_DigsStairs проверь это утверждение.");
    }

    @Test
    void plan_PitTwentyNoBlocksNoTool_DigsEmergencyStairs() {
        PitExitPlanner.Plan plan = PitExitPlanner.plan(
                FEET, PitTestWorld.pit(20, 0, BlockKind.HARD), new PitExitPlanner.Supplies(0, false));

        assertEquals(
                PitExitPlanner.Method.STAIRS,
                plan.method(),
                "Двадцатиблочная яма без ничего: аварийная лестница вверх"
                        + " Иначе тест plan_PitTwentyNoBlocksNoTool_DigsEmergencyStairs перестанет ловить эту поломку. При смене plan_PitTwentyNoBlocksNoTool_DigsEmergencyStairs проверь это утверждение.");
        assertTrue(
                plan.rise() >= 19,
                "Выход не оставляет духа на дне"
                        + " Иначе тест plan_PitTwentyNoBlocksNoTool_DigsEmergencyStairs перестанет ловить эту поломку. При смене plan_PitTwentyNoBlocksNoTool_DigsEmergencyStairs проверь это утверждение.");
    }

    @Test
    void plan_StoneNoToolWithBlocks_ChoosesPillarNotDig() {
        PitExitPlanner.Plan plan = PitExitPlanner.plan(
                FEET, PitTestWorld.pit(4, 0, BlockKind.HARD), new PitExitPlanner.Supplies(3, false));

        assertEquals(
                PitExitPlanner.Method.PILLAR,
                plan.method(),
                "Камень руками не берётся: при своих блоках дух ставит столб, а не тратит время на аварийную ломку"
                        + " Иначе тест plan_StoneNoToolWithBlocks_ChoosesPillarNotDig перестанет ловить эту поломку. При смене plan_StoneNoToolWithBlocks_ChoosesPillarNotDig проверь это утверждение.");
    }

    @Test
    void plan_WaterOnBottom_StillFindsDryEscape() {
        PitTestWorld world = PitTestWorld.pit(4, 0, BlockKind.SOFT);
        world.set(FEET, BlockKind.WATER);

        PitExitPlanner.Plan plan = PitExitPlanner.plan(FEET, world, new PitExitPlanner.Supplies(0, false));

        assertTrue(
                plan.possible(),
                "Вода на дне не отменяет выход: дух всплывает или режет лестницу"
                        + " Иначе тест plan_WaterOnBottom_StillFindsDryEscape перестанет ловить эту поломку. При смене plan_WaterOnBottom_StillFindsDryEscape проверь это утверждение.");
        assertFalse(
                plan.dig().contains(FEET),
                "Дух не ломает воду под собой"
                        + " При смене plan_WaterOnBottom_StillFindsDryEscape проверь это утверждение. Иначе поломка пройдёт незамеченной в прогоне.");
    }

    @Test
    void plan_GravelAbove_EscapesWithoutDiggingUnderIt() {
        PitTestWorld world = PitTestWorld.pit(5, 0, BlockKind.SOFT);
        world.set(new BlockCoord(1, 66, 0), BlockKind.FALLING);
        world.set(new BlockCoord(1, 67, 0), BlockKind.FALLING);

        PitExitPlanner.Plan plan = PitExitPlanner.plan(FEET, world, new PitExitPlanner.Supplies(0, false));

        assertTrue(
                plan.possible(),
                "Гравий сверху не запирает духа: он выбирает сторону без падающих блоков"
                        + " Иначе тест plan_GravelAbove_EscapesWithoutDiggingUnderIt перестанет ловить эту поломку. При смене plan_GravelAbove_EscapesWithoutDiggingUnderIt проверь это утверждение.");
        for (BlockCoord cell : plan.dig()) {
            assertFalse(
                    world.at(PitTestWorld.below(cell)) == BlockKind.FALLING
                            && !plan.dig().contains(PitTestWorld.below(cell)),
                    "Клетка под падающим гравием не выбивается без самого гравия: иначе он падает на духа"
                            + " При смене plan_GravelAbove_EscapesWithoutDiggingUnderIt проверь это утверждение.");
        }
    }

    @Test
    void plan_PitUnderWarehouse_ExitsAtFloorNotRoof() {
        PitExitPlanner.Plan plan =
                PitExitPlanner.plan(FEET, PitTestWorld.indoorPit(), new PitExitPlanner.Supplies(0, false));

        assertTrue(
                plan.possible(),
                "Яма под складом не запирает духа: выход ищется на уровне пола, а не крыши"
                        + " Иначе тест plan_PitUnderWarehouse_ExitsAtFloorNotRoof перестанет ловить эту поломку. При смене plan_PitUnderWarehouse_ExitsAtFloorNotRoof проверь это утверждение.");
        assertEquals(
                69,
                plan.exit().y(),
                "Выход ведёт на пол склада (69), а не на его крышу"
                        + " Иначе тест plan_PitUnderWarehouse_ExitsAtFloorNotRoof перестанет ловить эту поломку. При смене plan_PitUnderWarehouse_ExitsAtFloorNotRoof проверь это утверждение.");
    }

    @Test
    void plan_MineBelowWaterLevel_EscapesUp() {
        PitTestWorld world = PitTestWorld.pit(9, 0, BlockKind.HARD);
        world.set(new BlockCoord(1, 64, 0), BlockKind.WATER);
        world.set(new BlockCoord(1, 65, 0), BlockKind.WATER);

        PitExitPlanner.Plan plan = PitExitPlanner.plan(FEET, world, new PitExitPlanner.Supplies(0, true));

        assertTrue(
                plan.possible(),
                "Затопленный нижний горизонт шахты: дух выходит вверх, а не тонет"
                        + " Иначе тест plan_MineBelowWaterLevel_EscapesUp перестанет ловить эту поломку. При смене plan_MineBelowWaterLevel_EscapesUp проверь это утверждение.");
    }

    @Test
    void plan_CaveWithoutExit_DigsToSurface() {
        PitTestWorld world = PitTestWorld.pit(12, 2, BlockKind.HARD);

        PitExitPlanner.Plan plan = PitExitPlanner.plan(FEET, world, new PitExitPlanner.Supplies(0, true));

        assertEquals(
                PitExitPlanner.Method.STAIRS,
                plan.method(),
                "Пещера без прохода: дух режет лестницу к поверхности"
                        + " Иначе тест plan_CaveWithoutExit_DigsToSurface перестанет ловить эту поломку. При смене plan_CaveWithoutExit_DigsToSurface проверь это утверждение.");
        assertFalse(
                plan.dig().isEmpty(),
                "Без прохода выход строится ломкой"
                        + " Иначе тест plan_CaveWithoutExit_DigsToSurface перестанет ловить эту поломку. При смене plan_CaveWithoutExit_DigsToSurface проверь это утверждение.");
    }
}
