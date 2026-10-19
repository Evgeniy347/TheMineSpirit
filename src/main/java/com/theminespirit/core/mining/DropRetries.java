package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * Очередь мест, где дроп не удалось подобрать с первого раза. Пока в куче есть
 * недобытые блоки, такой отказ не валит задачу: следующие добытые блоки могут
 * освободить предмет (например, снять накрывающий блок), а в конце очередь
 * повторяется. Места ближе радиуса сбора считаются одним: один обход накрывает
 * дроп обеих точек.
 */
public final class DropRetries {
    private final int radius;
    private final Deque<BlockCoord> pending = new ArrayDeque<>();

    public DropRetries(int radius) {
        if (radius < 1) {
            throw new IllegalArgumentException(
                    "Радиус повтора сбора " + radius + " меньше единицы: повтор не накрыл бы даже клетку провала");
        }
        this.radius = radius;
    }

    public void failed(BlockCoord center) {
        Objects.requireNonNull(center, "Место провала сбора не может быть null: непонятно, что повторять");
        for (BlockCoord known : pending) {
            if (covers(known, center)) {
                return;
            }
        }
        pending.addLast(center);
    }

    public boolean isEmpty() {
        return pending.isEmpty();
    }

    public int size() {
        return pending.size();
    }

    public BlockCoord poll() {
        return pending.pollFirst();
    }

    private boolean covers(BlockCoord known, BlockCoord center) {
        return Math.abs(known.x() - center.x()) <= radius
                && Math.abs(known.y() - center.y()) <= radius
                && Math.abs(known.z() - center.z()) <= radius;
    }
}
