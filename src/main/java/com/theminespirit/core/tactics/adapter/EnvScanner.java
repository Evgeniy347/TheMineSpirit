package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.perception.Terrain;
import com.theminespirit.core.perception.TerrainView;
import com.theminespirit.core.tactics.EnvFacts;
import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class EnvScanner {
    public static final int RADIUS = 8;
    public static final int MAX_COVER_CANDIDATES = 3;
    public static final int MAX_DROP_PROBE = 8;
    public static final int COVER_RESERVE = 16;
    public static final double EYE_HEIGHT = 1.6;
    private static final int LATTICE = 2;
    private static final int[][] NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final List<int[]> COLUMNS = columns();

    private final CellCache cache = new CellCache();

    public EnvReading scan(Point self, BlockCoord feet, Optional<Point> threat, TerrainView world, ReadBudget budget,
            long now) {
        int total = budget.remaining();
        CellReader neighbours = new CellReader(world, cache, new ReadBudget(total), now);
        double drop = dropHeight(feet, neighbours);
        int afterDrop = neighbours.budget().used();
        CellReader columns = new CellReader(world, cache, new ReadBudget(Math.max(0, total - afterDrop)), now);
        Found found = scanColumns(self, feet, columns);
        int afterColumns = afterDrop + columns.budget().used();
        CellReader coverReader = new CellReader(world, cache, new ReadBudget(Math.max(0, total - afterColumns)), now);
        Cover cover = threat.map(source -> findCover(self, feet, source, found.walls, coverReader))
                .orElse(Cover.NONE);
        int used = afterColumns + coverReader.budget().used();
        budget.charge(used);
        boolean cut = neighbours.cutByBudget() || columns.cutByBudget() || coverReader.cutByBudget();
        return new EnvReading(found.lava, drop, found.water.map(Cell::coord),
                found.water.map(Cell::distance).orElse(EnvFacts.FAR_DISTANCE), cover.cell, cover.distance, used, cut);
    }

    private record Cell(BlockCoord coord, double distance) {
    }

    private record Found(double lava, Optional<Cell> water, List<BlockCoord> walls) {
    }

    private record Cover(Optional<BlockCoord> cell, double distance) {
        static final Cover NONE = new Cover(Optional.empty(), EnvFacts.FAR_DISTANCE);
    }

    private double dropHeight(BlockCoord feet, CellReader reader) {
        int deepest = 0;
        for (int[] step : NEIGHBOURS) {
            BlockCoord side = new BlockCoord(feet.x() + step[0], feet.y(), feet.z() + step[1]);
            Optional<Terrain> at = reader.at(side);
            if (at.isEmpty() || at.get() == Terrain.SOLID) {
                continue;
            }
            int depth = 0;
            for (int dy = 1; dy <= MAX_DROP_PROBE; dy++) {
                Optional<Terrain> below = reader.at(new BlockCoord(side.x(), side.y() - dy, side.z()));
                if (below.isEmpty() || below.get() != Terrain.AIR) {
                    break;
                }
                depth = dy;
            }
            deepest = Math.max(deepest, depth);
        }
        return deepest;
    }

    private Found scanColumns(Point self, BlockCoord feet, CellReader reader) {
        double lava = EnvFacts.FAR_DISTANCE;
        Optional<Cell> water = Optional.empty();
        List<BlockCoord> walls = new ArrayList<>();
        for (int[] offset : COLUMNS) {
            if (reader.cutByBudget() || (!walls.isEmpty() && reader.budget().remaining() <= COVER_RESERVE)) {
                break;
            }
            BlockCoord column = new BlockCoord(feet.x() + offset[0], feet.y(), feet.z() + offset[1]);
            Optional<Terrain> level = reader.at(column);
            if (level.isEmpty()) {
                continue;
            }
            BlockCoord seen = column;
            Terrain terrain = level.get();
            if (terrain == Terrain.SOLID) {
                Optional<Terrain> head = reader.at(new BlockCoord(column.x(), column.y() + 1, column.z()));
                if (head.isPresent() && head.get() == Terrain.SOLID) {
                    walls.add(column);
                }
                continue;
            }
            if (terrain == Terrain.AIR) {
                seen = new BlockCoord(column.x(), column.y() - 1, column.z());
                Optional<Terrain> below = reader.at(seen);
                if (below.isEmpty()) {
                    continue;
                }
                terrain = below.get();
            }
            double distance = self.distanceTo(center(seen));
            if (terrain == Terrain.LAVA) {
                lava = Math.min(lava, distance);
            } else if (terrain == Terrain.WATER && (water.isEmpty() || distance < water.get().distance())) {
                water = Optional.of(new Cell(seen, distance));
            }
        }
        return new Found(lava, water, walls);
    }

    private Cover findCover(Point self, BlockCoord feet, Point threat, List<BlockCoord> walls, CellReader reader) {
        Point threatEye = eye(threat);
        if (LineOfSight.trace(threatEye, eye(self), reader) == Visibility.BLOCKED) {
            return new Cover(Optional.of(feet), 0);
        }
        List<BlockCoord> nearest = walls.stream()
                .sorted(Comparator.comparingDouble((BlockCoord wall) -> self.distanceTo(center(wall)))
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
        Cover best = Cover.NONE;
        int candidates = 0;
        for (BlockCoord wall : nearest) {
            if (candidates >= MAX_COVER_CANDIDATES || reader.cutByBudget()) {
                break;
            }
            candidates++;
            BlockCoord hide = behind(wall, threat);
            if (standable(hide, reader)
                    && LineOfSight.trace(threatEye, eye(center(hide)), reader) == Visibility.BLOCKED) {
                double distance = self.distanceTo(center(hide));
                if (distance < best.distance) {
                    best = new Cover(Optional.of(hide), distance);
                }
            }
        }
        return best;
    }

    private static boolean standable(BlockCoord cell, CellReader reader) {
        return is(reader, cell, Terrain.AIR)
                && is(reader, new BlockCoord(cell.x(), cell.y() + 1, cell.z()), Terrain.AIR)
                && is(reader, new BlockCoord(cell.x(), cell.y() - 1, cell.z()), Terrain.SOLID);
    }

    private static boolean is(CellReader reader, BlockCoord cell, Terrain expected) {
        return reader.at(cell).filter(terrain -> terrain == expected).isPresent();
    }

    static BlockCoord behind(BlockCoord wall, Point threat) {
        double dx = wall.x() + 0.5 - threat.x();
        double dz = wall.z() + 0.5 - threat.z();
        int stepX = Math.abs(dx) * 2 >= Math.abs(dz) ? (int) Math.signum(dx) : 0;
        int stepZ = Math.abs(dz) * 2 >= Math.abs(dx) ? (int) Math.signum(dz) : 0;
        return new BlockCoord(wall.x() + stepX, wall.y(), wall.z() + stepZ);
    }

    private static Point center(BlockCoord cell) {
        return new Point(cell.x() + 0.5, cell.y(), cell.z() + 0.5);
    }

    private static Point eye(Point feet) {
        return new Point(feet.x(), feet.y() + EYE_HEIGHT, feet.z());
    }

    private static List<int[]> columns() {
        List<int[]> offsets = new ArrayList<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if ((dx != 0 || dz != 0) && dx * dx + dz * dz <= RADIUS * RADIUS) {
                    offsets.add(new int[] {dx, dz});
                }
            }
        }
        offsets.sort(Comparator.comparingInt(EnvScanner::latticeRank)
                .thenComparingInt(offset -> offset[0] * offset[0] + offset[1] * offset[1])
                .thenComparingInt(offset -> offset[0]).thenComparingInt(offset -> offset[1]));
        return List.copyOf(offsets);
    }

    private static int latticeRank(int[] offset) {
        return Math.floorMod(offset[0], LATTICE) == 0 && Math.floorMod(offset[1], LATTICE) == 0 ? 0 : 1;
    }
}
