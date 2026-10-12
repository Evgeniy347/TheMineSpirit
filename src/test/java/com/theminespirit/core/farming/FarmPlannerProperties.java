package com.theminespirit.core.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class FarmPlannerProperties {
    private static final int GRID = 6;
    private static final BlockCoord FROM = new BlockCoord(0, 1, 0);
    private static final Cell[] CELLS = Cell.values();
    private static final Ground[] GROUNDS = Ground.values();
    private static final Above[] ABOVES = {Above.AIR, Above.WEED, Above.BLOCKED};
    private static final CropKind[] CROPS = {CropKind.WHEAT, CropKind.CARROT, CropKind.POTATO, CropKind.BEETROOT};

    @Property
    void next_RandomInput_ReturnedTargetIsEligibleWithStand(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        MapFarmView farm = randomFarm(random);
        CountingSafety safety = randomSafety(random);
        Hydration hydration = randomHydration(random);
        List<BlockCoord> candidates = randomTargets(random);

        FarmPlanner.Outcome outcome = FarmPlanner.next(FarmJob.HARVEST, CropKind.ANY, FROM, candidates, farm,
                safety, hydration);

        if (outcome instanceof FarmPlanner.Next next) {
            FarmTile fresh = farm.peek(next.target());
            assertTrue(FarmJob.HARVEST.eligible(fresh, CropKind.ANY, false),
                    "Выбранная цель обязана подходить под работу на свежем тайле: иначе дух идёт к чужой клетке");
            assertTrue(FarmStandSpots.of(next.target(), FROM, safety, farm).contains(next.stand()),
                    "Выбранная стоянка обязана входить в набор допустимых для цели: иначе дух встанет в опасное место");
        }
    }

    @Property
    void next_RandomInput_IndependentOfCandidateOrder(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        MapFarmView farm = randomFarm(random);
        Hydration hydration = randomHydration(random);
        List<BlockCoord> candidates = randomTargets(random);
        List<BlockCoord> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, new Random((long) seed + 1));

        FarmPlanner.Outcome ordered = FarmPlanner.next(FarmJob.HARVEST, CropKind.ANY, FROM, candidates, farm,
                randomSafety(new Random(seed + 100)), hydration);
        FarmPlanner.Outcome reordered = FarmPlanner.next(FarmJob.HARVEST, CropKind.ANY, FROM, shuffled, farm,
                randomSafety(new Random(seed + 100)), hydration);

        assertEquals(ordered, reordered,
                "Результат не должен зависеть от порядка целей: ничья разрешается координатами, иначе "
                        + "работа непредсказуемо выбирает разные клетки");
    }

    @Property
    void next_DenseTillWorld_MatchesFullScanWithoutEarlyExit(
            @ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        MapFarmView farm = tillableFarm(random);
        CountingSafety safety = standableSafety(random);
        Hydration hydration = fullHydration();
        List<BlockCoord> candidates = wideTargets(random);
        BlockCoord from = new BlockCoord(random.nextInt(WIDE), 1, random.nextInt(WIDE));

        FarmPlanner.Outcome actual = FarmPlanner.next(FarmJob.TILL, CropKind.ANY, from, candidates, farm,
                safety, hydration);
        FarmPlanner.Outcome expected = referenceNext(FarmJob.TILL, CropKind.ANY, from, candidates, farm,
                safety, hydration);

        assertEquals(expected, actual,
                "Ранний выход обязан возвращать то же, что полный перебор: иначе остановка пропускает ближнюю цель "
                        + "со стоянкой на смещении до √11. Цели: " + candidates);
    }

    private static final int WIDE = 8;

    private static MapFarmView tillableFarm(Random random) {
        MapFarmView farm = new MapFarmView();
        for (int x = 0; x < WIDE; x++) {
            for (int z = 0; z < WIDE; z++) {
                Above above = random.nextBoolean() ? Above.AIR : Above.WEED;
                farm.set(x, 0, z, new FarmTile(random.nextBoolean() ? Ground.DIRT : Ground.GRASS, above,
                        Optional.empty()));
            }
        }
        return farm;
    }

    private static CountingSafety standableSafety(Random random) {
        CountingSafety safety = new CountingSafety().fillFloor(0, -4, -4, WIDE + 3, WIDE + 3);
        for (int x = -4; x < WIDE + 4; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = -4; z < WIDE + 4; z++) {
                    if (random.nextInt(10) == 0) {
                        safety.set(x, y, z, Cell.SOLID);
                    }
                }
            }
        }
        return safety;
    }

    private static Hydration fullHydration() {
        Set<BlockCoord> water = new HashSet<>();
        for (int x = 0; x < WIDE; x++) {
            for (int z = 0; z < WIDE; z++) {
                water.add(new BlockCoord(x, 0, z));
            }
        }
        return Hydration.of(water);
    }

    private static List<BlockCoord> wideTargets(Random random) {
        int count = random.nextInt(3, 11);
        List<BlockCoord> targets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            targets.add(new BlockCoord(random.nextInt(WIDE), 0, random.nextInt(WIDE)));
        }
        return targets;
    }

    private static FarmPlanner.Outcome referenceNext(FarmJob job, CropKind filter, BlockCoord from,
            List<BlockCoord> candidates, MapFarmView farm, SafetyView safety, Hydration hydration) {
        List<BlockCoord> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparingLong((BlockCoord target) -> distanceSquared(from, target))
                .thenComparing(Comparator.naturalOrder()));
        CachedSafetyView cached = new CachedSafetyView(safety);
        FarmPlanner.Next best = null;
        long bestStandSquared = Long.MAX_VALUE;
        int ineligible = 0;
        int noStand = 0;
        for (BlockCoord target : ordered) {
            FarmTile tile = farm.tileAt(target);
            boolean watered = job == FarmJob.TILL && hydration.watered(target);
            if (!job.eligible(tile, filter, watered)) {
                ineligible++;
                continue;
            }
            List<BlockCoord> stands = FarmStandSpots.of(target, from, cached, farm);
            if (stands.isEmpty()) {
                noStand++;
                continue;
            }
            FarmPlanner.Next candidate = new FarmPlanner.Next(target, stands.get(0));
            if (best == null || closer(candidate, best, from)) {
                best = candidate;
                bestStandSquared = distanceSquared(from, candidate.stand());
            }
        }
        return best == null ? new FarmPlanner.Exhausted(ineligible, noStand) : best;
    }

    private static boolean closer(FarmPlanner.Next candidate, FarmPlanner.Next current, BlockCoord from) {
        long candidateDistance = distanceSquared(from, candidate.stand());
        long currentDistance = distanceSquared(from, current.stand());
        if (candidateDistance != currentDistance) {
            return candidateDistance < currentDistance;
        }
        int byTarget = candidate.target().compareTo(current.target());
        if (byTarget != 0) {
            return byTarget < 0;
        }
        return candidate.stand().compareTo(current.stand()) < 0;
    }

    private static long distanceSquared(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        return dx * dx + dy * dy + dz * dz;
    }

    @Property
    void next_RandomInput_SafetyReadsBoundedByTargetCount(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        MapFarmView farm = randomFarm(random);
        CountingSafety safety = randomSafety(random);
        Hydration hydration = randomHydration(random);
        List<BlockCoord> candidates = randomTargets(random);

        FarmPlanner.next(FarmJob.HARVEST, CropKind.ANY, FROM, candidates, farm, safety, hydration);

        assertTrue(safety.reads() <= FarmPlanner.MAX_SAFETY_READS_PER_TARGET * candidates.size(),
                "Планирование читает не более " + FarmPlanner.MAX_SAFETY_READS_PER_TARGET + " клеток на цель: "
                        + "иначе перебор целей кладёт серверный тик. Чтений: " + safety.reads() + ", целей: "
                        + candidates.size());
    }

    private static MapFarmView randomFarm(Random random) {
        MapFarmView farm = new MapFarmView();
        for (int x = 0; x < GRID; x++) {
            for (int z = 0; z < GRID; z++) {
                Ground ground = GROUNDS[random.nextInt(GROUNDS.length)];
                Above above = ground == Ground.FARMLAND && random.nextBoolean() ? Above.CROP
                        : ABOVES[random.nextInt(ABOVES.length)];
                Optional<CropState> crop = Optional.empty();
                if (above == Above.CROP) {
                    CropKind kind = CROPS[random.nextInt(CROPS.length)];
                    crop = Optional.of(new CropState(kind, random.nextInt(kind.maxAge() + 1)));
                }
                farm.set(x, 0, z, new FarmTile(ground, above, crop));
            }
        }
        return farm;
    }

    private static CountingSafety randomSafety(Random random) {
        CountingSafety safety = new CountingSafety().fillFloor(0, -1, -1, GRID, GRID);
        for (int x = -1; x <= GRID; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = -1; z <= GRID; z++) {
                    safety.set(x, y, z, CELLS[random.nextInt(CELLS.length)]);
                }
            }
        }
        return safety;
    }

    private static Hydration randomHydration(Random random) {
        Set<BlockCoord> water = new HashSet<>();
        int count = random.nextInt(0, 6);
        for (int i = 0; i < count; i++) {
            water.add(new BlockCoord(random.nextInt(GRID), 0, random.nextInt(GRID)));
        }
        return Hydration.of(water);
    }

    private static List<BlockCoord> randomTargets(Random random) {
        int count = random.nextInt(1, 9);
        List<BlockCoord> targets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            targets.add(new BlockCoord(random.nextInt(GRID), 0, random.nextInt(GRID)));
        }
        return targets;
    }
}
