package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class BarricadePlan {
    private BarricadePlan() {
    }

    public static List<BlockCoord> seal(BlockCoord left, BlockCoord right, int height, BlockCoord spirit) {
        Objects.requireNonNull(left, "Шов без левого берега не строится: без сечения неизвестно, что запечатывать");
        Objects.requireNonNull(right, "Шов без правого берега не строится: без сечения неизвестно, что запечатывать");
        Objects.requireNonNull(spirit, "Шов без клетки духа не строится: без неё духа замуруют в собственной стене");
        if (height < 1) {
            throw new IllegalArgumentException("Высота шва = " + height
                    + ": шов ниже блока не остановит врага");
        }
        int spanX = Math.abs(right.x() - left.x());
        int spanZ = Math.abs(right.z() - left.z());
        if (spanX == 0 && spanZ == 0) {
            throw new IllegalArgumentException("Берега сошлись в одну линию: прохода нет и запечатывать нечего");
        }
        int baseY = Math.min(left.y(), right.y());
        List<BlockCoord> cells = new ArrayList<>();
        if (spanX >= spanZ) {
            int from = Math.min(left.x(), right.x());
            int to = Math.max(left.x(), right.x());
            for (int x = from; x <= to; x++) {
                for (int level = 0; level < height; level++) {
                    cells.add(new BlockCoord(x, baseY + level, left.z()));
                }
            }
        } else {
            int from = Math.min(left.z(), right.z());
            int to = Math.max(left.z(), right.z());
            for (int z = from; z <= to; z++) {
                for (int level = 0; level < height; level++) {
                    cells.add(new BlockCoord(left.x(), baseY + level, z));
                }
            }
        }
        
        cells.removeIf(cell -> cell.equals(spirit));
        cells.sort(Comparator.comparingInt(BlockCoord::y).thenComparingInt(BlockCoord::x).thenComparingInt(BlockCoord::z));
        return List.copyOf(cells);
    }
}
