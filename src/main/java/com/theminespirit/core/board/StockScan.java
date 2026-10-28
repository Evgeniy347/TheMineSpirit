package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class StockScan {

    public interface Blocks {
        boolean isLoaded(BlockCoord pos);

        boolean isContainer(BlockCoord pos);
    }

    public record Result(List<BlockCoord> containers, int blockReads) {
        public Result {
            Objects.requireNonNull(containers, "containers не может быть null: пустой список задают пустым");
            containers = List.copyOf(containers);
            if (blockReads < 0) {
                throw new IllegalArgumentException(
                        "blockReads равен " + blockReads + " и отрицателен: счётчик чтений не бывает меньше нуля");
            }
        }
    }

    private StockScan() {}

    public static List<BlockCoord> knownFirst(List<BlockCoord> known, List<BlockCoord> scanned) {
        Objects.requireNonNull(known, "Реестр сундуков не может быть null: пустой задают пустым списком");
        Objects.requireNonNull(scanned, "Найденные контейнеры не могут быть null: пустой задают пустым списком");
        Set<BlockCoord> all = new LinkedHashSet<>(known);
        all.addAll(scanned);
        return List.copyOf(all);
    }

    public static Result containers(
            List<BlockCoord> centers, int radiusBlocks, int heightBlocks, int blockBudget, Blocks blocks) {
        Objects.requireNonNull(centers, "centers не может быть null: обход идёт вокруг центров заявок");
        Objects.requireNonNull(blocks, "blocks не может быть null: без источника блоков обход бессмыслен");
        if (radiusBlocks < 0) {
            throw new IllegalArgumentException(
                    "radiusBlocks равен " + radiusBlocks + " и отрицателен: радиус обхода не бывает отрицательным");
        }
        if (heightBlocks < 0) {
            throw new IllegalArgumentException(
                    "heightBlocks равен " + heightBlocks + " и отрицателен: высота обхода не бывает отрицательной");
        }
        if (blockBudget <= 0) {
            throw new IllegalArgumentException(
                    "blockBudget равен " + blockBudget + " и не положителен: снимок без бюджета читает мир целиком");
        }
        List<BlockCoord> ordered = new ArrayList<>(new LinkedHashSet<>(centers));
        Set<BlockCoord> found = new HashSet<>();
        int reads = 0;
        for (BlockCoord center : ordered) {
            for (int dx = -radiusBlocks; dx <= radiusBlocks; dx++) {
                for (int dz = -radiusBlocks; dz <= radiusBlocks; dz++) {
                    for (int dy = -heightBlocks; dy <= heightBlocks; dy++) {
                        if (reads >= blockBudget) {
                            return new Result(order(found, ordered), reads);
                        }
                        BlockCoord pos = new BlockCoord(center.x() + dx, center.y() + dy, center.z() + dz);
                        reads++;
                        if (!blocks.isLoaded(pos)) {
                            continue;
                        }
                        if (blocks.isContainer(pos)) {
                            found.add(pos);
                        }
                    }
                }
            }
        }
        return new Result(order(found, ordered), reads);
    }

    private static List<BlockCoord> order(Set<BlockCoord> found, List<BlockCoord> centers) {
        List<BlockCoord> ordered = new ArrayList<>(found);
        ordered.sort(Comparator.comparingLong((BlockCoord pos) -> nearestDistance(pos, centers))
                .thenComparing(Comparator.naturalOrder()));
        return ordered;
    }

    private static long nearestDistance(BlockCoord pos, List<BlockCoord> centers) {
        long best = Long.MAX_VALUE;
        for (BlockCoord center : centers) {
            long dx = (long) pos.x() - center.x();
            long dy = (long) pos.y() - center.y();
            long dz = (long) pos.z() - center.z();
            best = Math.min(best, dx * dx + dy * dy + dz * dz);
        }
        return best;
    }
}
