package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class TargetBuffer {
    public static final int CAPACITY = 64;
    public static final int REFILL_THRESHOLD = 8;

    private final List<BlockCoord> active = new ArrayList<>();
    private final List<BlockCoord> surplus = new ArrayList<>();

    public void offer(List<BlockCoord> found) {
        Objects.requireNonNull(found, "Список найденных целей не может быть null: неизвестно, что положить в буфер");
        for (BlockCoord coord : found) {
            Objects.requireNonNull(coord, "Цель не может быть null: буфер хранит только координаты блоков");
            if (active.size() < CAPACITY) {
                active.add(coord);
            } else {
                surplus.add(coord);
            }
        }
    }

    public boolean needsRefill() {
        return active.size() < REFILL_THRESHOLD;
    }

    public List<BlockCoord> targets(BlockCoord from) {
        Objects.requireNonNull(from, "Точка духа не может быть null: по ней цели упорядочиваются по близости");
        List<BlockCoord> ordered = new ArrayList<>(active);
        ordered.sort(Comparator.comparingDouble((BlockCoord coord) -> distanceSquared(from, coord))
                .thenComparing(Comparator.naturalOrder()));
        return ordered;
    }

    public void remove(BlockCoord coord) {
        Objects.requireNonNull(coord, "Убираемая цель не может быть null: непонятно, что удалять");
        active.remove(coord);
        promote();
    }

    public void clearActive() {
        active.clear();
        promote();
    }

    public boolean isEmpty() {
        return active.isEmpty() && surplus.isEmpty();
    }

    public int activeSize() {
        return active.size();
    }

    public int surplusSize() {
        return surplus.size();
    }

    public int size() {
        return active.size() + surplus.size();
    }

    private void promote() {
        while (active.size() < CAPACITY && !surplus.isEmpty()) {
            active.add(surplus.remove(0));
        }
    }

    private static double distanceSquared(BlockCoord from, BlockCoord to) {
        double dx = (double) to.x() - from.x();
        double dy = (double) to.y() - from.y();
        double dz = (double) to.z() - from.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
