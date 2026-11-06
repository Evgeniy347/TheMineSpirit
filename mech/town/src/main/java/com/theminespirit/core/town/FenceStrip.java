package com.theminespirit.core.town;

import java.util.BitSet;
import java.util.Objects;

public final class FenceStrip {
    private FenceStrip() {}

    public static ColumnMask of(FenceLine ring, int width) {
        Objects.requireNonNull(ring, "Ограда не может быть null: полоса выравнивания идёт вдоль её линии");
        if (width < 0) {
            throw new IllegalArgumentException("Полоса ограды " + width + ": ширина не отрицательна");
        }
        if (ring.posts().isEmpty()) {
            return new ColumnMask(0, 0, 1, 1, new BitSet());
        }
        int x1 = Integer.MAX_VALUE;
        int z1 = Integer.MAX_VALUE;
        int x2 = Integer.MIN_VALUE;
        int z2 = Integer.MIN_VALUE;
        for (ColumnMask.Cell post : ring.posts()) {
            x1 = Math.min(x1, post.x() - width);
            z1 = Math.min(z1, post.z() - width);
            x2 = Math.max(x2, post.x() + width);
            z2 = Math.max(z2, post.z() + width);
        }
        int columns = x2 - x1 + 1;
        BitSet bits = new BitSet(columns * (z2 - z1 + 1));
        for (ColumnMask.Cell post : ring.posts()) {
            for (int dz = -width; dz <= width; dz++) {
                int row = (post.z() + dz - z1) * columns;
                bits.set(row + post.x() - width - x1, row + post.x() + width - x1 + 1);
            }
        }
        return new ColumnMask(x1, z1, columns, z2 - z1 + 1, bits);
    }
}
