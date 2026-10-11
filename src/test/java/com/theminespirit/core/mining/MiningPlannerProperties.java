package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class MiningPlannerProperties {
    private static final int GRID = 6;
    private static final BlockCoord FROM = new BlockCoord(0, 0, 0);
    private static final Cell[] CELLS = Cell.values();

    @Property(tries = 300)
    void next_RandomInput_ReturnedTargetIsSafeWithStand(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        CellWorld world = randomWorld(random);
        List<BlockCoord> remaining = randomTargets(random);

        MiningPlanner.Outcome outcome = MiningPlanner.next(FROM, remaining, world);

        if (outcome instanceof MiningPlanner.Next next) {
            assertTrue(MiningSafety.canBreak(next.target(), world) instanceof BreakSafety.Safe,
                    "Выбранная цель обязана быть безопасной: иначе дух пробивает блок с жидкостью за гранью");
            assertTrue(StandSpots.of(next.target(), world, FROM).contains(next.stand()),
                    "Выбранная стоянка обязана входить в набор допустимых для цели: иначе дух встанет в опасное место");
        }
    }

    @Property(tries = 300)
    void next_RandomInput_IndependentOfTargetOrder(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        CellWorld world = randomWorld(random);
        List<BlockCoord> remaining = randomTargets(random);
        List<BlockCoord> shuffled = new ArrayList<>(remaining);
        Collections.shuffle(shuffled, new Random((long) seed + 1));

        MiningPlanner.Outcome ordered = MiningPlanner.next(FROM, remaining, world);
        MiningPlanner.Outcome reordered = MiningPlanner.next(FROM, shuffled, world);

        assertEquals(ordered, reordered,
                "Результат не должен зависеть от порядка целей: ничья разрешается координатами, иначе "
                        + "добыча непредсказуемо выбирает разные блоки");
    }

    @Property(tries = 300)
    void next_RandomInput_ReadsBoundedByTargetCount(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        CellWorld world = randomWorld(random);
        List<BlockCoord> remaining = randomTargets(random);

        MiningPlanner.next(FROM, remaining, world);

        assertTrue(world.reads() <= MiningPlanner.MAX_VIEW_READS_PER_TARGET * remaining.size(),
                "Планирование читает не более " + MiningPlanner.MAX_VIEW_READS_PER_TARGET + " клеток на цель: "
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
