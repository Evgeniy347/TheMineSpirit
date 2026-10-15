package com.theminespirit.core.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.husbandry.RingGround.Cell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RingPlanTests {
    private static final Cell ANIMAL = new Cell(0, 0);

    @Test
    void wallClearance_ProductionConstant_IsTwoAboveFloor() {
        assertEquals(2, RingPlan.WALL_CLEARANCE,
                "Стенка обязана быть выше пола на 2: животное прыгает на 1, запас в 1 блок держит кольцо");
    }

    @Test
    void interiorSizes_ProductionConstants_MatchAgreedFootprint() {
        assertEquals(1, RingPlan.INTERIOR_SMALL,
                "Внутренность кольца для мелких обязана быть 1x1: цыплёнку тесно не бывает, а блоки лишние");
        assertEquals(2, RingPlan.INTERIOR_LARGE,
                "Внутренность кольца для крупных обязана быть 2x2: корова в 1x1 задыхается в стенке");
    }

    @Test
    void sizeFor_ChickenAndCattle_SplitsSmallAndLarge() {
        assertEquals(RingPlan.Size.SMALL, RingPlan.sizeFor(Species.CHICKEN),
                "Курица мелкая: ей хватает кольца 1x1, большое кольцо тратит блоки зря");
        for (Species large : List.of(Species.COW, Species.SHEEP, Species.PIG)) {
            assertEquals(RingPlan.Size.LARGE, RingPlan.sizeFor(large),
                    "Вид " + large + " крупный: в кольце 1x1 он застрянет в стенке");
        }
    }

    @Test
    void plan_FlatSmall_RingOfEightTwoBlocksHigh() {
        RingGround ground = RingGround.flat(-1, -1, 3, 3, 10);

        RingPlan.Ring ring = RingPlan.plan(ground, ANIMAL, RingPlan.Size.SMALL);

        assertEquals(Set.of(ANIMAL), ring.interior(),
                "Внутренность малого кольца обязана быть клеткой животного: иначе кольцо строится не вокруг него");
        assertEquals(8, ring.wall().size(),
                "Малое кольцо обязано держать 8 стенок вокруг 1x1: дыра в кольце выпускает животное первым шагом");
        assertEquals(16, ring.blocks().size(),
                "На ровной земле малое кольцо обязано быть из 16 блоков (8 стенок по 2): иначе стенку перепрыгивают");
        for (Map.Entry<Cell, Integer> wall : ring.tops().entrySet()) {
            assertEquals(12, wall.getValue(),
                    "На ровной земле верх стенки обязан быть на 2 выше пола: иначе животное выходит прыжком");
        }
    }

    @Test
    void plan_FlatLarge_RingOfTwelveTwoBlocksHigh() {
        RingGround ground = RingGround.flat(-1, -1, 4, 4, 7);

        RingPlan.Ring ring = RingPlan.plan(ground, ANIMAL, RingPlan.Size.LARGE);

        assertEquals(4, ring.interior().size(),
                "Внутренность большого кольца обязана быть 2x2: иначе крупное животное душится стенкой");
        assertEquals(12, ring.wall().size(),
                "Большое кольцо обязано держать 12 стенок вокруг 2x2: дыра в кольце выпускает животное первым шагом");
        assertEquals(24, ring.blocks().size(),
                "На ровной земле большое кольцо обязано быть из 24 блоков (12 стенок по 2): иначе стенку перепрыгивают");
    }

    @Test
    void plan_Slope_EveryWallTwoAboveNeighbourInsideFloor() {
        RingGround ground = RingGround.of(-2, -2, new int[][]{
                {8, 8, 9, 9, 9},
                {8, 8, 9, 9, 9},
                {8, 8, 9, 9, 9},
                {8, 8, 9, 9, 9},
                {8, 8, 9, 9, 9}});

        RingPlan.Ring ring = RingPlan.plan(ground, new Cell(1, 1), RingPlan.Size.SMALL);

        for (Cell wall : ring.wall()) {
            int highestInner = ring.interior().stream()
                    .filter(inner -> Math.abs(inner.x() - wall.x()) <= 1 && Math.abs(inner.z() - wall.z()) <= 1)
                    .mapToInt(ground::heightAt).max().orElseThrow();
            assertTrue(ring.tops().get(wall) - highestInner >= 2,
                    "Стенка " + wall + " обязана быть выше соседнего внутреннего пола на 2: на склоне низкую стенку "
                            + "животное берёт прыжком сверху");
            assertTrue(ring.tops().get(wall) - ground.heightAt(wall) >= 2,
                    "Стенка " + wall + " обязана быть выше собственного пола на 2: иначе её берут прыжком снаружи");
        }
    }

    @Test
    void plan_StepOneUp_LowSideColumnsThreeBlocksHigh() {
        RingGround ground = RingGround.of(-1, -1, new int[][]{
                {10, 10, 10},
                {10, 11, 10},
                {10, 10, 10}});

        RingPlan.Ring ring = RingPlan.plan(ground, ANIMAL, RingPlan.Size.SMALL);

        List<BlockCoord> westColumn = ring.blocks().stream()
                .filter(block -> block.x() == -1 && block.z() == 0).toList();
        assertEquals(3, westColumn.size(),
                "Стенка под ступенькой обязана быть из 3 блоков: 2 блока от низкой земли дают верх лишь на 1 выше "
                        + "высокого пола, и овца уходит прыжком");
        assertEquals(13, ring.tops().get(new Cell(-1, 0)),
                "Верх стенки под ступенькой обязан быть на 2 выше высокого пола (11 + 2): иначе проверка выхода "
                        + "найдёт слабое место");
    }

    @Test
    void plan_PitBesideRing_DeepSideColumnReachesTwoAboveInside() {
        RingGround ground = RingGround.of(-1, -1, new int[][]{
                {10, 10, 10},
                {10, 10, 8},
                {10, 10, 10}});

        RingPlan.Ring ring = RingPlan.plan(ground, ANIMAL, RingPlan.Size.SMALL);

        List<BlockCoord> eastColumn = ring.blocks().stream()
                .filter(block -> block.x() == 1 && block.z() == 0).toList();
        assertEquals(4, eastColumn.size(),
                "Стенка над ямой обязана дотянуться на 2 выше внутреннего пола: короткая стенка оставляет уступ, "
                        + "с которого животное выходит");
        assertEquals(Set.copyOf(eastColumn.stream().map(block -> block.y()).toList()),
                Set.of(8, 9, 10, 11),
                "Стенка над ямой обязана стоять от дна ямы без просветов: дыра внизу выпускает животное под стенкой");
    }

    @Test
    void plan_BlocksOrder_BottomUpSoLowerSupportsUpper() {
        RingGround ground = RingGround.of(-1, -1, new int[][]{
                {10, 10, 10},
                {10, 11, 10},
                {10, 10, 10}});

        List<BlockCoord> blocks = RingPlan.plan(ground, ANIMAL, RingPlan.Size.SMALL).blocks();

        for (int i = 1; i < blocks.size(); i++) {
            assertTrue(blocks.get(i - 1).y() <= blocks.get(i).y(),
                    "Блоки кольца обязаны идти снизу вверх: верхний без нижнего повисает без опоры");
        }
        assertEquals(blocks.size(), blocks.stream().collect(Collectors.toSet()).size(),
                "Дубли в плане кольца — двойной расход блоков: каждая клетка ставится один раз");
    }
}
