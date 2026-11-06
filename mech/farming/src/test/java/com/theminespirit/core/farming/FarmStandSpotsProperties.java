package com.theminespirit.core.farming;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class FarmStandSpotsProperties {
    private static final int GRID = 7;
    private static final double EYE_HEIGHT = 1.62;
    private static final double TARGET_CENTER_Y = 0.5;
    private static final FarmTile TILLABLE = new FarmTile(Ground.DIRT, Above.AIR, Optional.empty());

    @Property
    void of_RandomWorlds_EveryStandIsWithinExecutorReach(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        CountingSafety safety = randomSafety(random);
        MapFarmView farm = tillableFarm();
        int checked = 0;
        for (int i = 0; i < 4; i++) {
            BlockCoord target = randomCoord(random);
            BlockCoord from = randomCoord(random);
            for (BlockCoord stand : FarmStandSpots.of(target, from, safety, farm)) {
                checked++;
                double distance = eyeDistance(stand, target);
                assertTrue(
                        distance <= FarmTuning.REACH,
                        "Каждая стоянка обязана лежать в досягаемости исполнителя (" + FarmTuning.REACH
                                + " от глаз до центра цели, как проверяет сессия): иначе BlockBreaker отвергнет "
                                + "ломку и цель не обработается. Стоянка " + stand + ", цель " + target
                                + ", расстояние " + distance);
            }
        }
        Assume.that(checked > 0);
    }

    @Property
    void next_TillRandomWorlds_ChosenStandIsWithinExecutorReach(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        CountingSafety safety = randomSafety(random);
        MapFarmView farm = tillableFarm();
        List<BlockCoord> candidates = randomTargets(random);
        BlockCoord from = randomCoord(random);

        FarmPlanner.Outcome outcome =
                FarmPlanner.next(FarmJob.TILL, CropKind.ANY, from, candidates, farm, safety, fullHydration());

        if (outcome instanceof FarmPlanner.Next next) {
            double distance = eyeDistance(next.stand(), next.target());
            assertTrue(
                    distance <= FarmTuning.REACH,
                    "Стоянка, выбранная планировщиком, обязана быть в досягаемости исполнителя (" + FarmTuning.REACH
                            + " от глаз до центра цели): иначе сессия не сможет обработать выбранную клетку. "
                            + "Стоянка " + next.stand() + ", цель " + next.target() + ", расстояние " + distance);
        }
    }

    private static double eyeDistance(BlockCoord stand, BlockCoord target) {
        double dx = (double) stand.x() - target.x();
        double dy = (double) stand.y() + EYE_HEIGHT - ((double) target.y() + TARGET_CENTER_Y);
        double dz = (double) stand.z() - target.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static CountingSafety randomSafety(Random random) {
        CountingSafety safety = new CountingSafety().fillFloor(0, -4, -4, GRID + 3, GRID + 3);
        for (int x = -3; x <= GRID + 2; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = -3; z <= GRID + 2; z++) {
                    int roll = random.nextInt(20);
                    if (roll == 0) {
                        safety.set(x, y, z, Cell.SOLID);
                    } else if (roll == 1) {
                        safety.set(x, y, z, Cell.LAVA);
                    }
                }
            }
        }
        return safety;
    }

    private static MapFarmView tillableFarm() {
        return new MapFarmView().fillBed(0, 0, 0, GRID - 1, GRID - 1, TILLABLE);
    }

    private static Hydration fullHydration() {
        Set<BlockCoord> water = new HashSet<>();
        for (int x = 0; x < GRID; x++) {
            for (int z = 0; z < GRID; z++) {
                water.add(new BlockCoord(x, 0, z));
            }
        }
        return Hydration.of(water);
    }

    private static List<BlockCoord> randomTargets(Random random) {
        int count = random.nextInt(1, 6);
        List<BlockCoord> targets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            targets.add(randomCoord(random));
        }
        return targets;
    }

    private static BlockCoord randomCoord(Random random) {
        return new BlockCoord(random.nextInt(GRID), 0, random.nextInt(GRID));
    }
}
