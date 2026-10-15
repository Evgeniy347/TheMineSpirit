package com.theminespirit.core.husbandry;

import com.theminespirit.core.husbandry.RingGround.Cell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RingPlan {
    public static final int WALL_CLEARANCE = 2;
    public static final int INTERIOR_SMALL = 1;
    public static final int INTERIOR_LARGE = 2;

    public enum Size {
        SMALL,
        LARGE
    }

    public record Ring(Set<Cell> interior, Set<Cell> wall, Map<Cell, Integer> tops, List<BlockCoord> blocks) {
    }

    private RingPlan() {
    }

    public static Size sizeFor(Species species) {
        Objects.requireNonNull(species, "Вид животного не может быть null: без него неизвестен размер кольца");
        return species == Species.CHICKEN ? Size.SMALL : Size.LARGE;
    }

    public static Ring plan(RingGround ground, Cell animal, Size size) {
        Objects.requireNonNull(ground, "Карта высот не может быть null: без неё неизвестно, где земля");
        Objects.requireNonNull(animal, "Клетка животного не может быть null: без неё неизвестно, где центр кольца");
        Objects.requireNonNull(size, "Размер кольца не может быть null: мелким тесно в большом, крупным тесно в малом");
        Set<Cell> interior = interiorOf(animal, size);
        Set<Cell> wall = wallOf(interior);
        for (Cell cell : interior) {
            requireCovered(ground, cell, "внутренность кольца");
        }
        for (Cell cell : wall) {
            requireCovered(ground, cell, "стенка кольца");
        }
        Map<Cell, Integer> tops = new LinkedHashMap<>();
        List<BlockCoord> blocks = new ArrayList<>();
        for (Cell cell : wall) {
            int reference = ground.heightAt(cell);
            for (Cell inner : interior) {
                if (Math.abs(inner.x() - cell.x()) <= 1 && Math.abs(inner.z() - cell.z()) <= 1) {
                    reference = Math.max(reference, ground.heightAt(inner));
                }
            }
            int top = reference + WALL_CLEARANCE;
            tops.put(cell, top);
            for (int y = ground.heightAt(cell); y < top; y++) {
                blocks.add(new BlockCoord(cell.x(), y, cell.z()));
            }
        }
        blocks.sort(Comparator.comparingInt(BlockCoord::y).thenComparingInt(BlockCoord::x)
                .thenComparingInt(BlockCoord::z));
        return new Ring(Set.copyOf(interior), Set.copyOf(wall), Map.copyOf(tops), List.copyOf(blocks));
    }

    private static Set<Cell> interiorOf(Cell animal, Size size) {
        Set<Cell> interior = new LinkedHashSet<>();
        int extent = size == Size.SMALL ? INTERIOR_SMALL : INTERIOR_LARGE;
        for (int dx = 0; dx < extent; dx++) {
            for (int dz = 0; dz < extent; dz++) {
                interior.add(new Cell(animal.x() + dx, animal.z() + dz));
            }
        }
        return interior;
    }

    private static Set<Cell> wallOf(Set<Cell> interior) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Cell cell : interior) {
            minX = Math.min(minX, cell.x());
            minZ = Math.min(minZ, cell.z());
            maxX = Math.max(maxX, cell.x());
            maxZ = Math.max(maxZ, cell.z());
        }
        Set<Cell> wall = new LinkedHashSet<>();
        for (int x = minX - 1; x <= maxX + 1; x++) {
            for (int z = minZ - 1; z <= maxZ + 1; z++) {
                Cell cell = new Cell(x, z);
                if (!interior.contains(cell)) {
                    wall.add(cell);
                }
            }
        }
        return wall;
    }

    private static void requireCovered(RingGround ground, Cell cell, String what) {
        if (!ground.contains(cell)) {
            throw new IllegalArgumentException("Карта высот не покрывает " + what + " (" + cell.x() + ", "
                    + cell.z() + "): план кольца строится только по измеренной земле");
        }
    }
}
