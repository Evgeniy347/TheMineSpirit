package com.theminespirit.core.pickup;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class PickupSpot {
    public static final int RADIUS = 2;

    private static final Set<Cell> UNREACHABLE_NEIGHBORS = Set.of(Cell.LAVA, Cell.FIRE);

    private PickupSpot() {}

    public static BlockCoord accessCell(BlockCoord itemCell, SafetyView view) {
        Objects.requireNonNull(itemCell, "Клетка предмета не может быть null: без неё неизвестно, где подбирать");
        Objects.requireNonNull(view, "SafetyView не может быть null: по нему проверяется клетка доступа");
        BlockCoord above = offset(itemCell, 0, 1, 0);
        if (view.cellAt(above) == Cell.HAZARD) {
            return above;
        }
        return itemCell;
    }

    public static Optional<BlockCoord> forDrop(BlockCoord itemCell, BlockCoord from, SafetyView view) {
        List<BlockCoord> stands = standsFor(itemCell, from, view);
        return stands.isEmpty() ? Optional.empty() : Optional.of(stands.get(0));
    }

    public static List<BlockCoord> standsFor(BlockCoord itemCell, BlockCoord from, SafetyView view) {
        Objects.requireNonNull(itemCell, "Клетка предмета не может быть null: без неё неизвестно, где подбирать");
        Objects.requireNonNull(from, "Точка духа не может быть null: по ней выбирается ближайшая стоянка");
        Objects.requireNonNull(view, "SafetyView не может быть null: по нему проверяется безопасность стоянки");
        if (MiningSafety.canStand(itemCell, view)) {
            return List.of(itemCell);
        }
        if (view.cellAt(itemCell) != Cell.HAZARD) {
            return List.of();
        }
        List<BlockCoord> stands = new ArrayList<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if ((dx == 0 && dz == 0) || (Math.abs(dx) == RADIUS && Math.abs(dz) == RADIUS)) {
                    continue;
                }
                BlockCoord stand = new BlockCoord(itemCell.x() + dx, itemCell.y(), itemCell.z() + dz);
                if (canStandBesideHazard(stand, view)) {
                    stands.add(stand);
                }
            }
        }
        stands.sort(Comparator.comparingLong((BlockCoord stand) -> distanceSquared(from, stand))
                .thenComparing(Comparator.naturalOrder()));
        return List.copyOf(stands);
    }

    private static boolean canStandBesideHazard(BlockCoord feet, SafetyView view) {
        if (view.cellAt(feet) != Cell.PASSABLE) {
            return false;
        }
        if (view.cellAt(offset(feet, 0, 1, 0)) != Cell.PASSABLE) {
            return false;
        }
        BlockCoord floor = offset(feet, 0, -1, 0);
        if (view.cellAt(floor) != Cell.SOLID) {
            return false;
        }
        return ringSafe(feet, view) && ringSafe(floor, view);
    }

    private static boolean ringSafe(BlockCoord center, SafetyView view) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                if (UNREACHABLE_NEIGHBORS.contains(view.cellAt(offset(center, dx, 0, dz)))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static BlockCoord offset(BlockCoord coord, int dx, int dy, int dz) {
        return new BlockCoord(coord.x() + dx, coord.y() + dy, coord.z() + dz);
    }

    private static long distanceSquared(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
