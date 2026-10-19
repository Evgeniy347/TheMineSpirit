package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class MiningPlannerProperties {
    private static final int GRID = 6;
    private static final BlockCoord FROM = new BlockCoord(0, 0, 0);
    private static final Cell[] CELLS = Cell.values();

    @Property
    void next_RandomInput_ReturnedTargetIsSafeWithStand(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        CellWorld world = randomWorld(random);
        List<BlockCoord> remaining = randomTargets(random);

        MiningPlanner.Outcome outcome = MiningPlanner.next(FROM, remaining, world);

        if (outcome instanceof MiningPlanner.Next next) {
            assertTrue(
                    MiningSafety.canBreak(next.target(), world) instanceof BreakSafety.Safe,
                    "Выбранная цель обязана быть безопасной: иначе дух пробивает блок с жидкостью за гранью");
            assertTrue(
                    StandSpots.of(next.target(), world, FROM).contains(next.stand()),
                    "Выбранная стоянка обязана входить в набор допустимых для цели: иначе дух встанет в опасное место");
        }
    }

    @Property
    void next_RandomInput_IndependentOfTargetOrder(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        CellWorld world = randomWorld(random);
        List<BlockCoord> remaining = randomTargets(random);
        List<BlockCoord> shuffled = new ArrayList<>(remaining);
        Collections.shuffle(shuffled, new Random((long) seed + 1));

        MiningPlanner.Outcome ordered = MiningPlanner.next(FROM, remaining, world);
        MiningPlanner.Outcome reordered = MiningPlanner.next(FROM, shuffled, world);

        assertEquals(
                ordered,
                reordered,
                "Результат не должен зависеть от порядка целей: ничья разрешается координатами, иначе "
                        + "добыча непредсказуемо выбирает разные блоки");
    }

    @Property
    void next_ExcludedFirstStands_NeverReturnsExcludedStand(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        CellWorld world = randomWorld(random);
        List<BlockCoord> remaining = randomTargets(random);
        Set<BlockCoord> excluded = new HashSet<>();
        for (BlockCoord target : remaining) {
            List<BlockCoord> stands = StandSpots.of(target, world, FROM);
            if (!stands.isEmpty()) {
                excluded.add(stands.get(0));
            }
        }

        MiningPlanner.Outcome outcome = MiningPlanner.next(FROM, remaining, world, excluded);

        if (outcome instanceof MiningPlanner.Next next) {
            assertTrue(
                    !excluded.contains(next.stand()),
                    "Возвращённая стоянка обязана быть вне исключённых: иначе исключение глухих стоянок "
                            + "не работает и дух идёт в стену");
        }
    }

    @Property
    void next_DenseWorld_MatchesFullScanWithoutEarlyExit(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        List<BlockCoord> remaining = wideTargets(random);
        CellWorld world = denseWorld(random, remaining);
        BlockCoord from = new BlockCoord(random.nextInt(WIDE), 0, random.nextInt(WIDE));

        MiningPlanner.Outcome actual = MiningPlanner.next(from, remaining, world);
        MiningPlanner.Outcome expected = referenceNext(from, remaining, world);

        assertEquals(
                expected,
                actual,
                "Ранний выход обязан возвращать то же, что полный перебор: иначе остановка пропускает ближнюю цель "
                        + "со стоянкой на смещении до 3. Цели: " + remaining);
    }

    private static final int WIDE = 8;

    private static CellWorld denseWorld(Random random, List<BlockCoord> targets) {
        CellWorld world = new CellWorld();
        world.fill(-4, -4, -4, WIDE + 3, WIDE + 3, Cell.SOLID);
        for (int x = -4; x < WIDE + 4; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = -4; z < WIDE + 4; z++) {
                    if (random.nextInt(10) == 0) {
                        world.set(x, y, z, Cell.SOLID);
                    }
                }
            }
        }
        for (BlockCoord target : targets) {
            world.set(target.x(), target.y(), target.z(), Cell.SOLID);
        }
        return world;
    }

    private static List<BlockCoord> wideTargets(Random random) {
        int count = random.nextInt(3, 11);
        List<BlockCoord> targets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            targets.add(new BlockCoord(random.nextInt(WIDE), 0, random.nextInt(WIDE)));
        }
        return targets;
    }

    private static MiningPlanner.Outcome referenceNext(BlockCoord from, List<BlockCoord> candidates, CellWorld world) {
        List<BlockCoord> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparingLong((BlockCoord target) -> distanceSquared(from, target))
                .thenComparing(Comparator.naturalOrder()));
        MiningPlanner.Next best = null;
        int unsafe = 0;
        int noStand = 0;
        for (BlockCoord target : ordered) {
            if (!(MiningSafety.canBreak(target, world) instanceof BreakSafety.Safe)) {
                unsafe++;
                continue;
            }
            List<BlockCoord> stands = StandSpots.of(target, world, from);
            if (stands.isEmpty()) {
                noStand++;
                continue;
            }
            MiningPlanner.Next candidate = new MiningPlanner.Next(target, stands.get(0));
            if (best == null || closer(candidate, best, from)) {
                best = candidate;
            }
        }
        return best == null ? new MiningPlanner.Exhausted(unsafe, noStand, 0) : best;
    }

    private static boolean closer(MiningPlanner.Next candidate, MiningPlanner.Next current, BlockCoord from) {
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
    void next_RandomInput_ReadsBoundedByTargetCount(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        CellWorld world = randomWorld(random);
        List<BlockCoord> remaining = randomTargets(random);

        MiningPlanner.next(FROM, remaining, world);

        assertTrue(
                world.reads() <= 446 * remaining.size(),
                "Планирование читает не более " + 446 + " клеток на цель: "
                        + "иначе перебор целей кладёт серверный тик. Чтений: " + world.reads()
                        + ", целей: " + remaining.size());
    }

    private static CellWorld randomWorld(Random random) {
        CellWorld world = new CellWorld();
        for (int x = 0; x < GRID; x++) {
            for (int y = 0; y < GRID; y++) {
                for (int z = 0; z < GRID; z++) {
                    world.set(x, y, z, CELLS[random.nextInt(CELLS.length)]);
                }
            }
        }
        return world;
    }

    private static List<BlockCoord> randomTargets(Random random) {
        int count = random.nextInt(1, 9);
        List<BlockCoord> targets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            targets.add(new BlockCoord(random.nextInt(GRID), random.nextInt(GRID), random.nextInt(GRID)));
        }
        return targets;
    }
}
