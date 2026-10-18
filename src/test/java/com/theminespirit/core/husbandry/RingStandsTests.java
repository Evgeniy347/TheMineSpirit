package com.theminespirit.core.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RingStandsTests {
    private static final int GROUND = 10;
    private static final RingGround.Cell ANIMAL = new RingGround.Cell(0, 0);

    private static RingPlan.Ring flatRing() {
        return RingPlan.plan(RingGround.flat(-8, -8, 18, 18, GROUND), ANIMAL, RingPlan.Size.LARGE);
    }

    // Земля сплошная ниже GROUND; блоки из built уже стоят и тоже сплошные, выше — воздух.
    private static SafetyView world(Set<BlockCoord> built) {
        return coord -> coord.y() < GROUND || built.contains(coord)
                ? Cell.SOLID
                : Cell.PASSABLE;
    }

    @Test
    void of_WholeRingRemaining_FirstStandReachesMostRemainingBlocks() {
        RingPlan.Ring ring = flatRing();
        BlockCoord block = new BlockCoord(2, GROUND, 0);
        BlockCoord spirit = new BlockCoord(-8, GROUND, -8);

        List<BlockCoord> stands = RingStands.of(block, ring, ring.blocks(), world(Set.of()), spirit);

        assertFalse(stands.isEmpty(), "У восточной стенки на ровной земле обязаны найтись стоянки");
        BlockCoord first = stands.get(0);
        int best = RingStands.coverage(first, ring.blocks());
        for (BlockCoord stand : stands) {
            int reached = RingStands.coverage(stand, ring.blocks());
            assertTrue(reached < best || reached == best && distanceSquared(spirit, first) <= distanceSquared(spirit, stand),
                    "Первая стоянка обязана доставать больше всего недостроенных блоков, из равных — быть ближе "
                            + "к духу: со стоянки у ближней стены дальняя не достаётся, и дух, подходящий к каждому "
                            + "блоку по отдельности, не успевает замкнуть кольцо, пока животное уходит. Первая "
                            + first + " достаёт " + best + ", а " + stand + " — " + reached);
        }
    }

    private static long distanceSquared(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        return dx * dx + dy * dy + dz * dz;
    }

    @Test
    void of_EqualCoverage_NearestStandKeepsReachWithMarginNotNextToWall() {
        RingPlan.Ring ring = flatRing();
        BlockCoord block = new BlockCoord(2, GROUND, 0);

        List<BlockCoord> stands = RingStands.of(block, ring, List.of(block), world(Set.of()),
                new BlockCoord(-8, GROUND, 0));

        BlockCoord first = stands.get(0);
        assertEquals(SpiritReach.HORIZONTAL - RingStands.ARRIVAL_MARGIN, block.x() - first.x(),
                "При равном охвате первой идёт стоянка ближе к духу, на краю досягаемости с запасом в клетку: "
                        + "подход к соседней со стенкой клетке тратит шаги, а стоянка на самом краю после остановки "
                        + "подхода за 0.6 блока оставляет блок вне досягаемости. Фактически " + first);
    }

    @Test
    void of_FlatRing_EveryStandReachesBlockWithMarginAfterArrival() {
        RingPlan.Ring ring = flatRing();
        for (BlockCoord block : ring.blocks()) {
            for (BlockCoord stand : RingStands.of(block, ring, ring.blocks(), world(Set.of()),
                    new BlockCoord(-8, GROUND, -8))) {
                for (int dx = -RingStands.ARRIVAL_MARGIN; dx <= RingStands.ARRIVAL_MARGIN; dx++) {
                    for (int dz = -RingStands.ARRIVAL_MARGIN; dz <= RingStands.ARRIVAL_MARGIN; dz++) {
                        BlockCoord arrived = new BlockCoord(stand.x() + dx, stand.y(), stand.z() + dz);
                        assertTrue(SpiritReach.reachable(arrived, block),
                                "Дух, остановившийся в клетке рядом со стоянкой " + stand + " (" + arrived
                                        + "), обязан дотянуться до " + block + ": иначе BlockPlacer откажет "
                                        + "OUT_OF_REACH и блок кольца выпадет из плана после трёх попыток");
                    }
                }
            }
        }
    }

    @Test
    void of_RingHalfBuilt_SpiritNeverInsideOnTopOfRingOrInInterior() {
        RingPlan.Ring ring = flatRing();
        Set<BlockCoord> ringBlocks = new HashSet<>(ring.blocks());
        Set<BlockCoord> built = new HashSet<>(ring.blocks().subList(0, ring.blocks().size() / 2));
        for (BlockCoord block : ring.blocks()) {
            if (built.contains(block)) {
                continue;
            }
            for (BlockCoord stand : RingStands.of(block, ring, ring.blocks(), world(built),
                    new BlockCoord(8, GROUND, 8))) {
                BlockCoord head = new BlockCoord(stand.x(), stand.y() + 1, stand.z());
                BlockCoord floor = new BlockCoord(stand.x(), stand.y() - 1, stand.z());
                assertFalse(ringBlocks.contains(floor),
                        "Стоянка " + stand + " для " + block + " стоит на стенке кольца: на стенку в два блока "
                                + "без лестницы не забраться, подход к ней висит до тайм-аута, пока животное уходит");
                assertFalse(ringBlocks.contains(stand) || ringBlocks.contains(head),
                        "Стоянка " + stand + " для " + block + " занимает блок кольца: дух загородил бы стенку "
                                + "собой, BlockPlacer не ставит блок в живое существо");
                assertFalse(ring.interior().contains(new RingGround.Cell(stand.x(), stand.z())),
                        "Стоянка " + stand + " для " + block + " внутри кольца: дух запер бы себя вместе "
                                + "с животным");
            }
        }
    }
}
