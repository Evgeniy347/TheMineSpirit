package com.theminespirit.core.building;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Порядок стройки закрытой постройки с учётом досягаемости: на каждую клетку — стоянка, с которой её можно
 * поставить. Стены ставятся снаружи снизу вверх, затем дух заходит внутрь через проём, ставит крышу
 * (с временной опоры, если не дотянуться), последним — выход и блоки проёма.
 * Класс чистый: мир не читает, геометрия — из списка клеток и проёма.
 */
public final class BuildSiteOrder {
    public enum StandKind {
        OUTSIDE,
        INSIDE,
        ON_STRUCTURE,
        SCAFFOLD
    }

    public record Step(BlockCoord cell, BlockCoord stand, StandKind kind) {
        public Step {
            Objects.requireNonNull(cell, "Клетка шага не может быть null: без неё неизвестно, что ставить");
            Objects.requireNonNull(stand, "Стоянка шага не может быть null: без неё дух не знает, откуда ставить");
            Objects.requireNonNull(kind, "Вид стоянки не может быть null: без него неясно, нужны ли подмости");
            if (cell.equals(stand)) {
                throw new IllegalArgumentException("Стоянка совпала с клеткой постановки " + cell
                        + ": дух не может стоять там, куда обязан поставить блок");
            }
        }
    }

    private static final int[][] HORIZONTAL = {
            {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private BuildSiteOrder() {
    }

    public static List<Step> plan(List<BlockCoord> cells, BlockCoord doorway) {
        Objects.requireNonNull(cells, "Список клеток не может быть null: без него неизвестно, что строить");
        Set<BlockCoord> targets = new LinkedHashSet<>(cells);
        if (targets.isEmpty()) {
            return List.of();
        }
        Footprint box = Footprint.of(targets);
        Set<BlockCoord> doorwaySet = doorway == null ? Set.of() : Set.of(doorway);

        List<BlockCoord> walls = new ArrayList<>();
        List<BlockCoord> roof = new ArrayList<>();
        for (BlockCoord cell : targets) {
            if (doorwaySet.contains(cell)) {
                continue;
            }
            if (cell.y() == box.maxY()) {
                roof.add(cell);
            } else {
                walls.add(cell);
            }
        }
        sortBottomUp(walls);
        sortBottomUp(roof);

        List<Step> ordered = new ArrayList<>();
        Set<BlockCoord> placed = new LinkedHashSet<>();
        for (BlockCoord cell : walls) {
            ordered.add(wallStep(cell, targets, placed));
            placed.add(cell);
        }
        for (BlockCoord cell : roof) {
            ordered.add(roofStep(cell, targets, placed, box));
            placed.add(cell);
        }
        if (doorway != null) {
            ordered.add(new Step(doorway, outsideStand(doorway, targets), StandKind.OUTSIDE));
        }
        return List.copyOf(ordered);
    }

    private static Step wallStep(BlockCoord cell, Set<BlockCoord> targets, Set<BlockCoord> placed) {
        BlockCoord outside = outsideStand(cell, targets);
        if (outside != null) {
            return new Step(cell, outside, StandKind.OUTSIDE);
        }
        BlockCoord inside = insideStand(cell, targets);
        if (inside != null) {
            return new Step(cell, inside, StandKind.INSIDE);
        }
        BlockCoord structure = structureStand(cell, placed);
        if (structure != null) {
            return new Step(cell, structure, StandKind.ON_STRUCTURE);
        }
        throw new IllegalStateException("Клетка стены " + cell + " осталась без стоянки: порядок стройки неполон");
    }

    private static Step roofStep(BlockCoord cell, Set<BlockCoord> targets, Set<BlockCoord> placed, Footprint box) {
        BlockCoord structure = nearestStructureStand(cell, placed);
        if (structure != null) {
            return new Step(cell, structure, StandKind.ON_STRUCTURE);
        }
        BlockCoord outside = outsideStand(cell, targets);
        if (outside != null) {
            return new Step(cell, outside, StandKind.OUTSIDE);
        }
        return new Step(cell, new BlockCoord(cell.x(), cell.y() - 1, cell.z()), StandKind.SCAFFOLD);
    }

    private static BlockCoord nearestStructureStand(BlockCoord cell, Set<BlockCoord> placed) {
        BlockCoord best = null;
        long bestDistance = Long.MAX_VALUE;
        for (BlockCoord candidate : placed) {
            if (!reachableFrom(cell, candidate)) {
                continue;
            }
            long distance = distanceSquared(cell, candidate);
            if (distance < bestDistance
                    || (distance == bestDistance && (best == null || candidate.compareTo(best) < 0))) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static BlockCoord structureStand(BlockCoord cell, Set<BlockCoord> placed) {
        return nearestStructureStand(cell, placed);
    }

    private static boolean reachableFrom(BlockCoord cell, BlockCoord candidate) {
        int dy = cell.y() - candidate.y();
        int dx = Math.abs(cell.x() - candidate.x());
        int dz = Math.abs(cell.z() - candidate.z());
        if (dy < 0 || dy > 1) {
            return false;
        }
        return dx + dz <= 1;
    }

    private static BlockCoord outsideStand(BlockCoord cell, Set<BlockCoord> targets) {
        for (int[] offset : HORIZONTAL) {
            BlockCoord neighbour = new BlockCoord(cell.x() + offset[0], cell.y(), cell.z() + offset[2]);
            if (!targets.contains(neighbour) && !insideFootprint(neighbour, targets)) {
                return neighbour;
            }
        }
        return null;
    }

    private static BlockCoord insideStand(BlockCoord cell, Set<BlockCoord> targets) {
        for (int[] offset : HORIZONTAL) {
            BlockCoord neighbour = new BlockCoord(cell.x() + offset[0], cell.y(), cell.z() + offset[2]);
            if (!targets.contains(neighbour) && insideFootprint(neighbour, targets)) {
                return neighbour;
            }
        }
        return null;
    }

    private static boolean insideFootprint(BlockCoord cell, Set<BlockCoord> targets) {
        Footprint box = Footprint.of(targets);
        return cell.x() > box.minX() && cell.x() < box.maxX() && cell.z() > box.minZ() && cell.z() < box.maxZ();
    }

    private static long distanceSquared(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static void sortBottomUp(List<BlockCoord> cells) {
        cells.sort(Comparator.comparingInt(BlockCoord::y).thenComparingInt(BlockCoord::x).thenComparingInt(BlockCoord::z));
    }

    private record Footprint(int minX, int maxX, int minZ, int maxZ, int maxY) {
        static Footprint of(Set<BlockCoord> targets) {
            int minX = targets.stream().mapToInt(BlockCoord::x).min().orElseThrow();
            int maxX = targets.stream().mapToInt(BlockCoord::x).max().orElseThrow();
            int minZ = targets.stream().mapToInt(BlockCoord::z).min().orElseThrow();
            int maxZ = targets.stream().mapToInt(BlockCoord::z).max().orElseThrow();
            int maxY = targets.stream().mapToInt(BlockCoord::y).max().orElseThrow();
            return new Footprint(minX, maxX, minZ, maxZ, maxY);
        }
    }
}