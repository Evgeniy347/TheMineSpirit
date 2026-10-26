package com.theminespirit.core.ui;

import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public record MineGeometry(
        TownMapView.Bounds bounds, int minY, int maxY, Set<BlockCoord> air, Set<BlockCoord> floors, BuildBox tunnel) {
    private static final Comparator<BlockCoord> LOW =
            Comparator.comparingInt(BlockCoord::y).thenComparing(Comparator.naturalOrder());

    public MineGeometry {
        Objects.requireNonNull(bounds, "Границы среза шахты не могут быть null");
        Objects.requireNonNull(air, "Воздух спуска не может быть null: пусто — пустое множество");
        Objects.requireNonNull(floors, "Пол спуска не может быть null: пусто — пустое множество");
        Objects.requireNonNull(tunnel, "Куб шахтного хода не может быть null");
        if (minY > maxY) {
            throw new IllegalArgumentException(
                    "Высоты шахты " + minY + ".." + maxY + ": нижняя отметка не выше верхней");
        }
        air = Set.copyOf(air);
        floors = Set.copyOf(floors);
    }

    public static MineGeometry of(MineLayout layout, int tunnelSize) {
        Objects.requireNonNull(layout, "Спуск шахты не может быть null: по нему считается срез");
        BlockCoord stand = layout.air().stream().min(LOW).orElse(layout.entrance());
        BuildBox tunnel = BuildBox.cube(stand.x(), stand.y(), stand.z(), tunnelSize);
        TownMapView.Bounds bounds = bounds(layout.air(), layout.floors(), tunnel);
        int minY = Math.min(tunnel.minY(), lowest(layout.air(), layout.floors()));
        int maxY = Math.max(tunnel.maxY(), highest(layout.air(), layout.floors()));
        return new MineGeometry(bounds, minY, maxY, Set.copyOf(layout.air()), Set.copyOf(layout.floors()), tunnel);
    }

    public int defaultY() {
        return Math.max(minY, Math.min(maxY, tunnel.minY()));
    }

    private static TownMapView.Bounds bounds(List<BlockCoord> air, List<BlockCoord> floors, BuildBox tunnel) {
        int x1 = tunnel.minX();
        int z1 = tunnel.minZ();
        int x2 = tunnel.maxX();
        int z2 = tunnel.maxZ();
        for (BlockCoord cell : all(air, floors)) {
            x1 = Math.min(x1, cell.x());
            z1 = Math.min(z1, cell.z());
            x2 = Math.max(x2, cell.x());
            z2 = Math.max(z2, cell.z());
        }
        return new TownMapView.Bounds(x1, z1, x2, z2);
    }

    private static int lowest(List<BlockCoord> air, List<BlockCoord> floors) {
        return all(air, floors).stream().mapToInt(BlockCoord::y).min().orElse(Integer.MAX_VALUE);
    }

    private static int highest(List<BlockCoord> air, List<BlockCoord> floors) {
        return all(air, floors).stream().mapToInt(BlockCoord::y).max().orElse(Integer.MIN_VALUE);
    }

    private static List<BlockCoord> all(List<BlockCoord> air, List<BlockCoord> floors) {
        return Stream.concat(air.stream(), floors.stream()).toList();
    }
}
