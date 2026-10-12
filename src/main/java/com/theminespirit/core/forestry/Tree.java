package com.theminespirit.core.forestry;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record Tree(BlockCoord base, List<BlockCoord> logs) implements TreeDetection {
    public Tree {
        Objects.requireNonNull(base, "base не может быть null: от нижнего бревна считается досягаемость рубки");
        Objects.requireNonNull(logs, "logs не может быть null: дерево без брёвен не бывает");
        logs = List.copyOf(logs);
        if (logs.isEmpty()) {
            throw new IllegalArgumentException("Дерево без брёвен: основание " + base + " должно быть среди них");
        }
        if (!logs.contains(base)) {
            throw new IllegalArgumentException("Основание " + base + " отсутствует среди брёвен " + logs
                    + ": порядок рубки отсчитывается от нижнего бревна ствола");
        }
        logs = logs.stream().sorted(chopOrder(base)).toList();
    }

    private static Comparator<BlockCoord> chopOrder(BlockCoord base) {
        return Comparator.comparingInt(BlockCoord::y)
                .thenComparingLong(coord -> distanceSquared(coord, base))
                .thenComparingInt(BlockCoord::x)
                .thenComparingInt(BlockCoord::z);
    }

    private static long distanceSquared(BlockCoord first, BlockCoord second) {
        long dx = (long) first.x() - second.x();
        long dy = (long) first.y() - second.y();
        long dz = (long) first.z() - second.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
