package com.theminespirit.core.forestry;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class ChopStands {
    public static final int FAR_RADIUS = 3;

    private static final int NEAR_RADIUS = 1;
    private static final int STAND_HEIGHT = 2;
    private static final int DROP_DEPTH = 3;
    private static final int STAND_SCAN_DEPTH = 64;
    private static final double EYE_HEIGHT = 1.5;
    private static final double MAX_LOG_DISTANCE = 4.0;

    public record Stand(BlockCoord feet, @Nullable BlockCoord climbFrom) {
        public Stand {
            Objects.requireNonNull(feet, "Клетка стоянки не может быть null: без неё дух не знает, куда встать");
            if (climbFrom != null && climbFrom.y() >= feet.y()) {
                throw new IllegalArgumentException(
                        "Опора подмостей " + climbFrom + " не ниже стоянки " + feet + ": столб строят вверх от опоры");
            }
        }
    }

    public interface View {
        boolean standable(BlockCoord feet);

        default boolean free(BlockCoord cell) {
            return standable(cell);
        }
    }

    private ChopStands() {}

    public static List<BlockCoord> stands(Tree tree, View view) {
        return standPoints(tree, view).stream().map(Stand::feet).toList();
    }

    public static List<Stand> standPoints(Tree tree, View view) {
        Objects.requireNonNull(tree, "Дерево не может быть null: без него неизвестно, где стоянка у ствола");
        Objects.requireNonNull(view, "Взгляд на мир не может быть null: по нему проверяется стоянка");
        Set<BlockCoord> walk = new LinkedHashSet<>();
        Set<BlockCoord> logs = Set.copyOf(tree.logs());
        Map<BlockCoord, BlockCoord> climb = new LinkedHashMap<>();
        int floor = tree.base().y() - STAND_SCAN_DEPTH;
        for (BlockCoord log : tree.logs()) {
            for (int dy = 0; dy < STAND_HEIGHT; dy++) {
                for (int dx = -NEAR_RADIUS; dx <= NEAR_RADIUS; dx++) {
                    for (int dz = -NEAR_RADIUS; dz <= NEAR_RADIUS; dz++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        BlockCoord feet = new BlockCoord(log.x() + dx, log.y() + dy, log.z() + dz);
                        if (logs.contains(feet) || logs.contains(below(feet))) {
                            continue;
                        }
                        if (walk.contains(feet) || climb.containsKey(feet)) {
                            continue;
                        }
                        if (view.standable(feet)) {
                            walk.add(feet);
                            continue;
                        }
                        BlockCoord base = climbBase(feet, view, floor);
                        if (base != null) {
                            climb.put(feet, base);
                        }
                    }
                }
            }
        }
        List<Stand> result = new ArrayList<>(walk.size() + climb.size());
        walk.forEach(feet -> result.add(new Stand(feet, null)));
        if (walk.isEmpty()) {
            collect(tree, view, FAR_RADIUS, -DROP_DEPTH, STAND_HEIGHT - 1)
                    .forEach(feet -> result.add(new Stand(feet, null)));
        }
        if (result.isEmpty()) {
            groundBelow(tree, view).forEach(feet -> result.add(new Stand(feet, null)));
        }
        climb.forEach((feet, base) -> result.add(new Stand(feet, base)));
        return List.copyOf(result);
    }

    private static @Nullable BlockCoord climbBase(BlockCoord feet, View view, int floor) {
        if (!view.free(feet) || !view.free(above(feet))) {
            return null;
        }
        for (int y = feet.y() - 1; y >= floor; y--) {
            BlockCoord cell = new BlockCoord(feet.x(), y, feet.z());
            if (!view.free(cell) || !view.free(above(cell))) {
                return null;
            }
            if (view.standable(cell)) {
                return cell;
            }
        }
        return null;
    }

    private static BlockCoord above(BlockCoord cell) {
        return new BlockCoord(cell.x(), cell.y() + 1, cell.z());
    }

    private static BlockCoord below(BlockCoord cell) {
        return new BlockCoord(cell.x(), cell.y() - 1, cell.z());
    }

    private static List<BlockCoord> groundBelow(Tree tree, View view) {
        Set<BlockCoord> logs = Set.copyOf(tree.logs());
        int baseY = tree.base().y();
        int floor = Math.max(-64, baseY - STAND_SCAN_DEPTH);
        List<BlockCoord> stands = new ArrayList<>();
        for (BlockCoord log : tree.logs()) {
            if (log.y() != baseY) {
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int y = baseY - 1; y >= floor; y--) {
                        BlockCoord feet = new BlockCoord(log.x() + dx, y, log.z() + dz);
                        if (logs.contains(feet) || !view.standable(feet)) {
                            continue;
                        }
                        stands.add(feet);
                        break;
                    }
                }
            }
        }
        return List.copyOf(new LinkedHashSet<>(stands));
    }

    private static List<BlockCoord> collect(Tree tree, View view, int radius, int minDy, int maxDy) {
        Objects.requireNonNull(tree, "Дерево не может быть null: без него неизвестно, где стоянка у ствола");
        Objects.requireNonNull(view, "Взгляд на мир не может быть null: по нему проверяется стоянка");
        Set<BlockCoord> logs = Set.copyOf(tree.logs());
        int baseY = tree.base().y();
        List<BlockCoord> stands = new ArrayList<>();
        for (BlockCoord log : tree.logs()) {
            if (log.y() != baseY) {
                continue;
            }
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    for (int dy = minDy; dy <= maxDy; dy++) {
                        BlockCoord feet = new BlockCoord(log.x() + dx, baseY + dy, log.z() + dz);
                        BlockCoord below = new BlockCoord(feet.x(), feet.y() - 1, feet.z());
                        if (logs.contains(feet)
                                || logs.contains(below)
                                || !view.standable(feet)
                                || !withinReach(feet, log)) {
                            continue;
                        }
                        stands.add(feet);
                    }
                }
            }
        }
        return List.copyOf(new LinkedHashSet<>(stands));
    }

    private static boolean withinReach(BlockCoord feet, BlockCoord log) {
        double dx = feet.x() - log.x();
        double dz = feet.z() - log.z();
        double dy = log.y() + 0.5 - (feet.y() + EYE_HEIGHT);
        return dx * dx + dz * dz + dy * dy <= MAX_LOG_DISTANCE * MAX_LOG_DISTANCE;
    }
}
