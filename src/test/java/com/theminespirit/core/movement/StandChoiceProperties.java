package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class StandChoiceProperties {
    @Property(tries = 300)
    void choose_CandidateOrderShuffled_SameStand(
            @ForAll @IntRange(min = 0, max = 1_000_000) int seed,
            @ForAll @IntRange(min = -100, max = 100) int tx,
            @ForAll @IntRange(min = 0, max = 100) int ty,
            @ForAll @IntRange(min = -100, max = 100) int tz,
            @ForAll @IntRange(min = -6, max = 6) int sdx,
            @ForAll @IntRange(min = -6, max = 6) int sdz) {
        BlockCoord target = new BlockCoord(tx, ty, tz);
        BlockCoord spirit = new BlockCoord(tx + sdx, ty + 1, tz + sdz);
        CountingSafety world = openGround(target);
        List<BlockCoord> shuffled = new ArrayList<>(StandChoice.candidates(target));
        Collections.shuffle(shuffled, new Random(seed));

        assertEquals(bruteBest(shuffled, target, spirit, world),
                StandChoice.choose(target, spirit, world),
                "Выбор стоянки обязан не зависеть от порядка кандидатов: иначе один и тот же подход "
                        + "даёт разные стоянки от запуска к запуску");
    }

    private static CountingSafety openGround(BlockCoord target) {
        CountingSafety world = new CountingSafety();
        for (int x = target.x() - 5; x <= target.x() + 5; x++) {
            for (int z = target.z() - 5; z <= target.z() + 5; z++) {
                world.set(x, target.y(), z, Cell.SOLID);
            }
        }
        return world;
    }

    private static Optional<BlockCoord> bruteBest(List<BlockCoord> candidates, BlockCoord target,
            BlockCoord spirit, CountingSafety world) {
        double centerX = target.x() + 0.5;
        double centerY = target.y() + 0.5;
        double centerZ = target.z() + 0.5;
        Comparator<BlockCoord> cheapestFirst = Comparator
                .comparingInt((BlockCoord stand) -> MoveCost.walkTicks(spirit, stand))
                .thenComparingDouble(stand -> euclid(spirit, stand))
                .thenComparing(Comparator.naturalOrder());
        Optional<BlockCoord> best = Optional.empty();
        for (BlockCoord stand : candidates) {
            double dx = stand.x() + 0.5 - centerX;
            double dy = stand.y() + StandChoice.EYE_HEIGHT - centerY;
            double dz = stand.z() + 0.5 - centerZ;
            if (dx * dx + dy * dy + dz * dz > StandChoice.REACH_DISTANCE * StandChoice.REACH_DISTANCE) {
                continue;
            }
            if (!MiningSafety.canStand(stand, world)) {
                continue;
            }
            if (best.isEmpty() || cheapestFirst.compare(stand, best.orElseThrow()) < 0) {
                best = Optional.of(stand);
            }
        }
        return best;
    }

    private static double euclid(BlockCoord spirit, BlockCoord stand) {
        double dx = stand.x() - spirit.x();
        double dy = stand.y() + StandChoice.EYE_HEIGHT - (spirit.y() + StandChoice.EYE_HEIGHT);
        double dz = stand.z() - spirit.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
