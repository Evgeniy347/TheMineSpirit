package com.theminespirit.core.board.lease;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class RollbackReach {
    public enum Action {
        CLIMB,
        STEP_DOWN,
        DIG
    }

    public record Step(Action action, int rise) {
        public Step {
            Objects.requireNonNull(action, "Действие подхода не может быть null: дух не знает шага");
            if (action == Action.CLIMB ? rise < 1 : rise != 0) {
                throw new IllegalArgumentException("Шаг " + action + " с подъёмом " + rise
                        + ": подъём положителен, остальные шаги высоту не несут");
            }
        }
    }

    public interface Ground {
        int NO_GROUND = Integer.MIN_VALUE;

        int feet(int x, int z);
    }

    private RollbackReach() {}

    public static Step next(int rise, boolean onOwnScaffold) {
        if (rise > 0) {
            return new Step(Action.CLIMB, rise);
        }
        if (rise == 0) {
            return new Step(Action.DIG, 0);
        }
        return new Step(onOwnScaffold ? Action.STEP_DOWN : Action.DIG, 0);
    }

    public static Optional<BlockCoord> climbStand(BlockCoord target, Ground ground, int radius) {
        Objects.requireNonNull(target, "Клетка отката не может быть null: некуда строить подмости");
        Objects.requireNonNull(ground, "Рельеф не может быть null: без него не найти стоянку");
        if (radius < 1) {
            throw new IllegalArgumentException("Радиус стоянки " + radius + " меньше 1: искать нечего");
        }
        List<BlockCoord> stands = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = target.x() + dx;
                int z = target.z() + dz;
                int y = ground.feet(x, z);
                if (y == Ground.NO_GROUND || y >= target.y()) {
                    continue;
                }
                stands.add(new BlockCoord(x, y, z));
            }
        }
        stands.sort(Comparator.comparingInt((BlockCoord stand) -> beside(stand, target) ? 0 : 1)
                .thenComparingInt(stand -> square(stand.x() - target.x()) + square(stand.z() - target.z()))
                .thenComparing(Comparator.comparingInt(BlockCoord::y).reversed()));
        return stands.stream().findFirst();
    }

    private static boolean beside(BlockCoord stand, BlockCoord target) {
        return stand.x() != target.x() || stand.z() != target.z();
    }

    private static int square(int value) {
        return value * value;
    }
}
