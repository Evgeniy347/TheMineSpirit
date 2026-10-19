package com.theminespirit.core.fishing;

import com.theminespirit.core.fishing.WaterView.Column;
import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class FishingSpots {
    public record Spot(BlockCoord stand, BlockCoord water) {
        public Spot {
            Objects.requireNonNull(stand, "Стоянка у воды не может быть null: с неё дух забрасывает удочку");
            Objects.requireNonNull(water, "Клетка воды не может быть null: в неё дух забрасывает удочку");
        }
    }

    /** Отбор места по внешним правилам мира: близость к поселению, чужие постройки. */
    public interface SpotFilter {
        boolean allow(BlockCoord stand, BlockCoord water);

        SpotFilter ALL = (stand, water) -> true;
    }

    private FishingSpots() {
    }

    public static Optional<Spot> find(BlockCoord origin, WaterView view, FishingData data) {
        Objects.requireNonNull(data, "Данные рыбалки не могут быть null: в них порог площади и глубины");
        return find(origin, view, data.minWidth(), data.minDepth(), SpotFilter.ALL);
    }

    public static Optional<Spot> findWorkPoint(BlockCoord origin, WaterView view, FishingData data,
            SpotFilter filter) {
        Objects.requireNonNull(data, "Данные рыбалки не могут быть null: в них размер рабочей точки");
        return find(origin, view, data.workWidth(), data.workDepth(), filter);
    }

    public static Optional<Spot> find(BlockCoord origin, WaterView view, int width, int depth,
            SpotFilter filter) {
        Objects.requireNonNull(origin, "Точка отсчёта не может быть null: по ней выбирается ближайший берег");
        Objects.requireNonNull(view, "Вид воды не может быть null: без него нечего выбирать");
        Objects.requireNonNull(filter, "Фильтр места не может быть null: пустой отбор задают SpotFilter.ALL");
        Map<Long, Column> surface = new LinkedHashMap<>();
        for (Column column : view.columns()) {
            if (column.depth() >= depth) {
                surface.put(key(column.x(), column.z()), column);
            }
        }
        Spot best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Column anchor : surface.values()) {
            if (!areaAt(surface, anchor, width)) {
                continue;
            }
            Spot spot = spotFor(anchor, width, origin, view);
            if (spot == null || !filter.allow(spot.stand(), spot.water())) {
                continue;
            }
            long distance = distanceSquared(origin, spot.stand());
            if (best == null || distance < bestDistance
                    || (distance == bestDistance && spot.stand().compareTo(best.stand()) < 0)) {
                best = spot;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean areaAt(Map<Long, Column> surface, Column anchor, int width) {
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < width; dz++) {
                Column column = surface.get(key(anchor.x() + dx, anchor.z() + dz));
                if (column == null || column.surfaceY() != anchor.surfaceY()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Spot spotFor(Column anchor, int width, BlockCoord origin, WaterView view) {
        BlockCoord bestStand = null;
        long bestDistance = Long.MAX_VALUE;
        for (int dx = -1; dx <= width; dx++) {
            for (int dz = -1; dz <= width; dz++) {
                boolean inside = dx >= 0 && dx < width && dz >= 0 && dz < width;
                if (inside) {
                    continue;
                }
                for (int level = 0; level <= 1; level++) {
                    BlockCoord stand = new BlockCoord(anchor.x() + dx, anchor.surfaceY() + level, anchor.z() + dz);
                    if (!view.canStand(stand)) {
                        continue;
                    }
                    long distance = distanceSquared(origin, stand);
                    if (bestStand == null || distance < bestDistance
                            || (distance == bestDistance && stand.compareTo(bestStand) < 0)) {
                        bestStand = stand;
                        bestDistance = distance;
                    }
                }
            }
        }
        if (bestStand == null) {
            return null;
        }
        BlockCoord water = nearestWater(anchor, width, bestStand);
        return new Spot(bestStand, water);
    }

    private static BlockCoord nearestWater(Column anchor, int width, BlockCoord stand) {
        BlockCoord best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int dx = 0; dx < width; dx++) {
            for (int dz = 0; dz < width; dz++) {
                BlockCoord cell = new BlockCoord(anchor.x() + dx, anchor.surfaceY(), anchor.z() + dz);
                long distance = distanceSquared(stand, cell);
                if (best == null || distance < bestDistance
                        || (distance == bestDistance && cell.compareTo(best) < 0)) {
                    best = cell;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    private static long distanceSquared(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
