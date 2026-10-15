package com.theminespirit.core.husbandry;

import com.theminespirit.core.husbandry.RingGround.Cell;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

public final class EscapeCheck {
    public static final int STEP_UP_MAX = 1;

    public record WeakSpot(Cell cell, List<Cell> path) {
    }

    private EscapeCheck() {
    }

    public static Optional<WeakSpot> findEscape(RingGround ground, Map<Cell, Integer> tops, Set<Cell> interior) {
        Objects.requireNonNull(ground, "Карта высот не может быть null: без неё неизвестно, где земля");
        Objects.requireNonNull(tops, "Верха стенок не могут быть null: без них непонятно, что построено");
        Objects.requireNonNull(interior, "Внутренность кольца не может быть null: без неё неизвестно, откуда выход");
        if (interior.isEmpty()) {
            throw new IllegalArgumentException("Внутренность кольца пуста: выход ищут из клетки животного");
        }
        for (Cell cell : interior) {
            requireCovered(ground, cell);
        }
        for (Cell cell : tops.keySet()) {
            requireCovered(ground, cell);
        }
        Map<Cell, Cell> parent = new HashMap<>();
        Set<Cell> seen = new HashSet<>(interior);
        Queue<Cell> queue = new ArrayDeque<>(interior);
        while (!queue.isEmpty()) {
            Cell current = queue.remove();
            if (!interior.contains(current) && !tops.containsKey(current)) {
                return Optional.of(weakSpot(ground, tops, parent, current));
            }
            for (Cell next : orthogonal(current)) {
                if (!ground.contains(next) || !seen.add(next)) {
                    continue;
                }
                if (surface(ground, tops, next) - surface(ground, tops, current) <= STEP_UP_MAX) {
                    parent.put(next, current);
                    queue.add(next);
                }
            }
            for (int[] diagonal : DIAGONALS) {
                Cell next = new Cell(current.x() + diagonal[0], current.z() + diagonal[1]);
                if (!ground.contains(next) || !seen.add(next)) {
                    continue;
                }
                if (surface(ground, tops, next) - surface(ground, tops, current) > STEP_UP_MAX) {
                    continue;
                }
                Cell first = new Cell(current.x() + diagonal[0], current.z());
                Cell second = new Cell(current.x(), current.z() + diagonal[1]);
                if (blocked(ground, tops, current, first) && blocked(ground, tops, current, second)) {
                    continue;
                }
                parent.put(next, current);
                queue.add(next);
            }
        }
        return Optional.empty();
    }

    private static final int[][] DIAGONALS = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};

    private static List<Cell> orthogonal(Cell cell) {
        return List.of(new Cell(cell.x() + 1, cell.z()), new Cell(cell.x() - 1, cell.z()),
                new Cell(cell.x(), cell.z() + 1), new Cell(cell.x(), cell.z() - 1));
    }

    private static boolean blocked(RingGround ground, Map<Cell, Integer> tops, Cell from, Cell side) {
        return ground.contains(side) && surface(ground, tops, side) - surface(ground, tops, from) > STEP_UP_MAX;
    }

    private static int surface(RingGround ground, Map<Cell, Integer> tops, Cell cell) {
        return Math.max(ground.heightAt(cell), tops.getOrDefault(cell, Integer.MIN_VALUE));
    }

    private static WeakSpot weakSpot(RingGround ground, Map<Cell, Integer> tops, Map<Cell, Cell> parent, Cell exit) {
        List<Cell> path = new ArrayList<>();
        for (Cell cell = exit; cell != null; cell = parent.get(cell)) {
            path.add(0, cell);
        }
        Set<Cell> candidates = new HashSet<>();
        for (Cell cell : path) {
            if (tops.containsKey(cell)) {
                candidates.add(cell);
            }
        }
        for (int i = 1; i < path.size(); i++) {
            Cell from = path.get(i - 1);
            Cell to = path.get(i);
            int dx = to.x() - from.x();
            int dz = to.z() - from.z();
            if (Math.abs(dx) == 1 && Math.abs(dz) == 1) {
                for (Cell side : List.of(new Cell(from.x() + dx, from.z()), new Cell(from.x(), from.z() + dz))) {
                    if (tops.containsKey(side)) {
                        candidates.add(side);
                    }
                }
            }
        }
        if (candidates.isEmpty()) {
            return new WeakSpot(exit, List.copyOf(path));
        }
        Cell weakest = candidates.stream()
                .min(Comparator.comparingInt((Cell cell) -> surface(ground, tops, cell))
                        .thenComparingInt(Cell::x).thenComparingInt(Cell::z))
                .orElseThrow();
        return new WeakSpot(weakest, List.copyOf(path));
    }

    private static void requireCovered(RingGround ground, Cell cell) {
        Objects.requireNonNull(cell, "Клетка кольца не может быть null: план строится только из живых клеток");
        if (!ground.contains(cell)) {
            throw new IllegalArgumentException("Карта высот не покрывает клетку (" + cell.x() + ", " + cell.z()
                    + "): проверка выхода идёт только по измеренной земле");
        }
    }
}
