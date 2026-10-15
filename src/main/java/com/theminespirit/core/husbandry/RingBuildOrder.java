package com.theminespirit.core.husbandry;

import com.theminespirit.core.husbandry.RingGround.Cell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class RingBuildOrder {
    private RingBuildOrder() {
    }

    public static Optional<BlockCoord> nextTarget(List<BlockCoord> remaining, BlockCoord current, Cell animal) {
        Objects.requireNonNull(remaining, "Остаток кольца не может быть null: без него неизвестно, что строить");
        Objects.requireNonNull(animal, "Клетка животного не может быть null: без неё неизвестен ближайший блок");
        if (current != null) {
            return Optional.of(current);
        }
        return remaining.stream().min(Comparator
                .comparingInt((BlockCoord block) -> distance(block, animal))
                .thenComparingInt(BlockCoord::y)
                .thenComparingInt(BlockCoord::x)
                .thenComparingInt(BlockCoord::z));
    }

    public static List<BlockCoord> order(RingPlan.Ring ring, Cell animalAtPlan) {
        Objects.requireNonNull(ring, "Кольцо не может быть null: без него неизвестно, что строить");
        Objects.requireNonNull(animalAtPlan, "Клетка животного не может быть null: без неё неизвестно, откуда "
                + "начинать замыкание");
        List<BlockCoord> ordered = new ArrayList<>(ring.blocks());
        ordered.sort(Comparator
                .comparingInt((BlockCoord block) -> distance(block, animalAtPlan))
                .thenComparingInt(BlockCoord::y)
                .thenComparingInt(BlockCoord::x)
                .thenComparingInt(BlockCoord::z));
        return List.copyOf(ordered);
    }

    private static int distance(BlockCoord block, Cell animal) {
        int dx = block.x() - animal.x();
        int dz = block.z() - animal.z();
        return dx * dx + dz * dz;
    }
}