package com.theminespirit.core.pickup;

import com.theminespirit.core.board.WorkStand;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class LeafDropStand {
    private static final int RADIUS = WorkStand.RADIUS;

    private LeafDropStand() {}

    public static Optional<BlockCoord> of(BlockCoord leaf, WorkStand.Ground ground) {
        Objects.requireNonNull(leaf, "Клетка листвы не может быть null: от неё ищут стоянку на земле");
        Objects.requireNonNull(ground, "Рельеф не может быть null: по нему берут высоту стоянки");
        for (int[] offset : offsets()) {
            int x = leaf.x() + offset[0];
            int z = leaf.z() + offset[1];
            BlockCoord feet = new BlockCoord(x, ground.feet(x, z), z);
            if (ground.free(feet)) {
                return Optional.of(feet);
            }
        }
        return Optional.empty();
    }

    private static List<int[]> offsets() {
        List<int[]> offsets = new ArrayList<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                offsets.add(new int[] {dx, dz});
            }
        }
        offsets.sort(Comparator.comparingInt((int[] offset) -> offset[0] * offset[0] + offset[1] * offset[1])
                .thenComparingInt(offset -> offset[0])
                .thenComparingInt(offset -> offset[1]));
        return offsets;
    }
}
