package com.theminespirit.core.policy;

import com.theminespirit.core.observation.BlockBox;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;

public record WorkZone(BlockCoord center, int radius) {
    public WorkZone {
        Objects.requireNonNull(center, "Центр рабочей зоны не может быть null: зона строится вокруг точки");
        if (radius < 1) {
            throw new IllegalArgumentException(
                    "Радиус рабочей зоны " + radius + " меньше 1: зона радиуса 0 запретила бы духу любое движение, "
                            + "для снятия зоны используйте «zone clear»");
        }
    }

    public BlockBox box() {
        return BlockBox.cube(center, radius);
    }

    public int excess(BlockCoord pos) {
        BlockBox box = box();
        return Math.max(
                axisExcess(pos.x(), box.min().x(), box.max().x()),
                Math.max(
                        axisExcess(pos.y(), box.min().y(), box.max().y()),
                        axisExcess(pos.z(), box.min().z(), box.max().z())));
    }

    public BlockCoord nearestInside(BlockCoord pos) {
        BlockBox box = box();
        return new BlockCoord(
                clamp(pos.x(), box.min().x(), box.max().x()),
                clamp(pos.y(), box.min().y(), box.max().y()),
                clamp(pos.z(), box.min().z(), box.max().z()));
    }

    private static int axisExcess(int value, int min, int max) {
        return Math.max(Math.max(min - value, value - max), 0);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
