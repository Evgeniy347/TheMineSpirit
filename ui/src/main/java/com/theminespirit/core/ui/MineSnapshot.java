package com.theminespirit.core.ui;

import com.theminespirit.core.building.BuildBox;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.mining.MineOres;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record MineSnapshot(
        TownMapView.Bounds bounds,
        int minY,
        int maxY,
        int y,
        List<Cell> cells,
        List<Rect> rects,
        List<MapMarker> markers) {
    public static final int ANY_Y = Integer.MIN_VALUE;
    public static final String DESCENT = "Спуск";
    public static final String TUNNEL = "Шахтный ход";
    private static final int PERCENT = 100;

    public enum Kind {
        PASSAGE,
        DESCENT,
        FLOOR,
        ORE,
        LUCK_ORE,
        WATER,
        LAVA
    }

    public record Cell(int x, int z, Kind kind) {
        public Cell {
            Objects.requireNonNull(kind, "Вид клетки среза шахты не может быть null");
        }
    }

    public record Rect(int x1, int z1, int x2, int z2, String label, int percent) {
        public Rect {
            Objects.requireNonNull(label, "Подпись постройки шахты не может быть null");
            if (x2 < x1 || z2 < z1 || percent < 0 || percent > PERCENT) {
                throw new IllegalArgumentException("Прямоугольник шахты " + x1 + "," + z1 + "–" + x2 + "," + z2
                        + " с готовностью " + percent + "%: углы по возрастанию, готовность 0..100");
            }
        }

        public boolean covers(double x, double z) {
            return x >= x1 && x < x2 + 1 && z >= z1 && z < z2 + 1;
        }

        public long area() {
            return (long) (x2 - x1 + 1) * (z2 - z1 + 1);
        }
    }

    public MineSnapshot {
        Objects.requireNonNull(bounds, "Границы среза шахты не могут быть null");
        if (y < minY || y > maxY) {
            throw new IllegalArgumentException("Срез шахты на y=" + y + " вне высот " + minY + ".." + maxY);
        }
        cells = List.copyOf(Objects.requireNonNull(cells, "Клетки среза шахты не могут быть null"));
        rects = List.copyOf(Objects.requireNonNull(rects, "Постройки шахты не могут быть null"));
        markers = List.copyOf(Objects.requireNonNull(markers, "Метки шахты не могут быть null"));
    }

    public MineSnapshot withMarkers(List<MapMarker> next) {
        return new MineSnapshot(bounds, minY, maxY, y, cells, rects, next);
    }

    public static MineSnapshot of(MineGeometry geometry, int requestedY, MineBlocks blocks) {
        Objects.requireNonNull(geometry, "Геометрия шахты не может быть null");
        Objects.requireNonNull(blocks, "Блоки мира не могут быть null: по ним считается срез");
        int y = requestedY == ANY_Y
                ? geometry.defaultY()
                : Math.max(geometry.minY(), Math.min(geometry.maxY(), requestedY));
        return new MineSnapshot(
                geometry.bounds(),
                geometry.minY(),
                geometry.maxY(),
                y,
                cells(geometry, y, blocks),
                rects(geometry, blocks),
                List.of());
    }

    private static List<Cell> cells(MineGeometry geometry, int y, MineBlocks blocks) {
        List<Cell> out = new ArrayList<>();
        TownMapView.Bounds bounds = geometry.bounds();
        for (int x = bounds.x1(); x <= bounds.x2(); x++) {
            for (int z = bounds.z1(); z <= bounds.z2(); z++) {
                Cell cell = cell(geometry, blocks, x, y, z);
                if (cell != null) {
                    out.add(cell);
                }
            }
        }
        return List.copyOf(out);
    }

    private static @Nullable Cell cell(MineGeometry geometry, MineBlocks blocks, int x, int y, int z) {
        BlockCoord at = new BlockCoord(x, y, z);
        MineBlocks.Observed observed = blocks.at(at);
        boolean openAbove = isOpenAbove(blocks.at(new BlockCoord(x, y + 1, z)));
        Kind kind = kind(observed, openAbove, geometry.air().contains(at));
        return kind == null ? null : new Cell(x, z, kind);
    }

    private static boolean isOpenAbove(MineBlocks.Observed above) {
        return above.empty()
                || "water".equals(path(above.blockId()))
                || "lava".equals(path(above.blockId()))
                || MineOres.kindOf(above.blockId()) != MineOres.Kind.NONE;
    }

    private static @Nullable Kind kind(MineBlocks.Observed observed, boolean openAbove, boolean descent) {
        String block = path(observed.blockId());
        if ("water".equals(block)) {
            return Kind.WATER;
        }
        if ("lava".equals(block)) {
            return Kind.LAVA;
        }
        MineOres.Kind ore = MineOres.kindOf(observed.blockId());
        if (ore == MineOres.Kind.LUCK) {
            return Kind.LUCK_ORE;
        }
        if (ore == MineOres.Kind.ORDINARY) {
            return Kind.ORE;
        }
        if (descent) {
            return Kind.DESCENT;
        }
        if (observed.empty()) {
            return Kind.PASSAGE;
        }
        return openAbove && SchematicBlocks.isFloorClass(observed.blockId()) ? Kind.FLOOR : null;
    }

    private static List<Rect> rects(MineGeometry geometry, MineBlocks blocks) {
        List<Rect> out = new ArrayList<>(2);
        if (!geometry.air().isEmpty()) {
            TownMapView.Bounds bounds = geometry.descentBounds();
            out.add(new Rect(
                    bounds.x1(), bounds.z1(), bounds.x2(), bounds.z2(), DESCENT, descentPercent(geometry, blocks)));
        }
        BuildBox tunnel = geometry.tunnel();
        out.add(new Rect(
                tunnel.minX(),
                tunnel.minZ(),
                tunnel.maxX(),
                tunnel.maxZ(),
                TUNNEL,
                percent(empties(tunnel.cells(), blocks), (int) tunnel.volume())));
        return List.copyOf(out);
    }

    private static int descentPercent(MineGeometry geometry, MineBlocks blocks) {
        int done = 0;
        for (BlockCoord cell : geometry.air()) {
            if (blocks.at(cell).empty()) {
                done++;
            }
        }
        for (BlockCoord cell : geometry.floors()) {
            if (SchematicBlocks.isFloorClass(blocks.at(cell).blockId())) {
                done++;
            }
        }
        return percent(done, geometry.air().size() + geometry.floors().size());
    }

    private static int empties(List<BlockCoord> cells, MineBlocks blocks) {
        int done = 0;
        for (BlockCoord cell : cells) {
            if (blocks.at(cell).empty()) {
                done++;
            }
        }
        return done;
    }

    private static int percent(int done, int total) {
        return total == 0 ? 0 : done * PERCENT / total;
    }

    private static String path(String blockId) {
        int colon = blockId.indexOf(':');
        return colon < 0 ? blockId : blockId.substring(colon + 1);
    }
}
