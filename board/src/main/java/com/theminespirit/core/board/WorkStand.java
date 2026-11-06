package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

public final class WorkStand {
    public static final int RADIUS = 3;
    private static final int STEP = 1;

    private WorkStand() {}

    public interface Ground {
        int feet(int x, int z);

        boolean free(BlockCoord feet);
    }

    public static Optional<BlockCoord> near(BlockCoord point, Ground ground) {
        return near(point, ground, feet -> true);
    }

    public static Optional<BlockCoord> near(BlockCoord point, Ground ground, Predicate<BlockCoord> reachable) {
        Objects.requireNonNull(point, "Точка работы не может быть null: стоянку ищут возле неё");
        Objects.requireNonNull(ground, "Рельеф не может быть null: высоту стоянки берут по текущему миру");
        Objects.requireNonNull(reachable, "Проверка пути не может быть null: иначе стоянка снова будет за преградой");
        List<int[]> offsets = new ArrayList<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                offsets.add(new int[] {dx, dz});
            }
        }
        offsets.sort(Comparator.comparingInt((int[] offset) -> offset[0] * offset[0] + offset[1] * offset[1])
                .thenComparingInt(offset -> offset[0])
                .thenComparingInt(offset -> offset[1]));
        List<BlockCoord> free = new ArrayList<>();
        for (int[] offset : offsets) {
            int x = point.x() + offset[0];
            int z = point.z() + offset[1];
            BlockCoord feet = new BlockCoord(x, ground.feet(x, z), z);
            if (ground.free(feet) && reachable.test(feet)) {
                free.add(feet);
            }
        }
        return free.stream()
                .filter(feet -> Math.abs(feet.y() - point.y()) <= STEP)
                .findFirst()
                .or(() -> free.stream().findFirst());
    }
}
