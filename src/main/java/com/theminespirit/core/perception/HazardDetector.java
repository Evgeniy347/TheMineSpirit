package com.theminespirit.core.perception;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.Optional;

public final class HazardDetector {
    public static final int DROP_DEPTH = 3;
    private static final int[][] HORIZONTAL_STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private HazardDetector() {}

    public static Optional<HazardType> classify(TerrainView view, BlockCoord pos) {
        Objects.requireNonNull(view, "view не может быть null: без обзора местности опасности не определить");
        Objects.requireNonNull(pos, "pos не может быть null: классифицируется конкретная клетка");
        return switch (view.at(pos)) {
            case LAVA -> Optional.of(HazardType.LAVA);
            case WATER -> Optional.of(HazardType.WATER);
            case AIR -> isEdgeOfDrop(view, pos) ? Optional.of(HazardType.DROP) : Optional.empty();
            case SOLID, UNLOADED -> Optional.empty();
        };
    }

    public static HazardRef refOf(HazardType type, BlockCoord pos, BlockCoord origin) {
        Objects.requireNonNull(origin, "origin не может быть null: расстояние считается от точки духа");
        double dx = pos.x() - origin.x();
        double dy = pos.y() - origin.y();
        double dz = pos.z() - origin.z();
        return new HazardRef(type, pos, Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    private static boolean isEdgeOfDrop(TerrainView view, BlockCoord pos) {
        if (view.at(offset(pos, 0, -1, 0)) != Terrain.SOLID) {
            return false;
        }
        for (int[] step : HORIZONTAL_STEPS) {
            if (fallsAway(view, offset(pos, step[0], 0, step[1]))) {
                return true;
            }
        }
        return false;
    }

    private static boolean fallsAway(TerrainView view, BlockCoord neighbor) {
        if (view.at(neighbor) != Terrain.AIR) {
            return false;
        }
        for (int depth = 1; depth <= DROP_DEPTH; depth++) {
            if (view.at(offset(neighbor, 0, -depth, 0)) != Terrain.AIR) {
                return false;
            }
        }
        return true;
    }

    private static BlockCoord offset(BlockCoord pos, int dx, int dy, int dz) {
        return new BlockCoord(pos.x() + dx, pos.y() + dy, pos.z() + dz);
    }
}
