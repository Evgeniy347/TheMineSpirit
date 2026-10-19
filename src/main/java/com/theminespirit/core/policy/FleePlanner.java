package com.theminespirit.core.policy;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Queue;
import java.util.Set;

public final class FleePlanner {
    public static final int DEFAULT_MAX_NODES = 200;
    public static final int DEFAULT_MAX_RADIUS = 8;
    public static final int READS_PER_NODE = 6;
    public static final double CLOSEST_PASS = 3.0;

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private FleePlanner() {}

    public record Threat(BlockCoord pos, double minDistance) {
        public Threat {
            Objects.requireNonNull(pos, "Позиция угрозы не может быть null: от неё считается расстояние");
            if (!(minDistance > 0) || Double.isInfinite(minDistance)) {
                throw new IllegalArgumentException("Расстояние держания " + minDistance
                        + " должно быть конечным и больше нуля: " + "нулевое расстояние ничего не требует от духа");
            }
        }

        public double distance(BlockCoord cell) {
            double dx = cell.x() - pos.x();
            double dy = cell.y() - pos.y();
            double dz = cell.z() - pos.z();
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        public double slack(BlockCoord cell) {
            return distance(cell) - minDistance;
        }
    }

    public record Plan(ReflexAction action, int visited) {
        public Plan {
            Objects.requireNonNull(action, "Действие плана не может быть null: исполнителю нечего делать");
        }
    }

    public static Plan plan(BlockCoord origin, List<Threat> threats, SafetyView view) {
        return plan(origin, threats, view, DEFAULT_MAX_NODES, DEFAULT_MAX_RADIUS);
    }

    public static Plan plan(BlockCoord origin, List<Threat> threats, SafetyView view, int maxNodes, int maxRadius) {
        Objects.requireNonNull(origin, "Исходная клетка не может быть null: от неё идёт поиск в ширину");
        Objects.requireNonNull(threats, "Список угроз не может быть null: пустой список задаётся List.of()");
        Objects.requireNonNull(view, "SafetyView не может быть null: по нему читается проходимость клеток");
        if (maxNodes < 1) {
            throw new IllegalArgumentException(
                    "maxNodes " + maxNodes + " меньше 1: без хотя бы исходной клетки поиск не начинается");
        }
        if (maxRadius < 0) {
            throw new IllegalArgumentException(
                    "maxRadius " + maxRadius + " отрицателен: радиус поиска считается от исходной клетки");
        }
        BudgetView budget = new BudgetView(view, maxNodes * READS_PER_NODE);
        Queue<BlockCoord> queue = new ArrayDeque<>();
        Set<BlockCoord> seen = new HashSet<>();
        queue.add(origin);
        seen.add(origin);
        double[] closestPass = closestPass(origin, threats);
        double originSlack = worstSlack(origin, threats);
        List<Visit> improving = new ArrayList<>();
        while (!queue.isEmpty() && !budget.exhausted()) {
            BlockCoord cell = queue.poll();
            double slack = worstSlack(cell, threats);
            if (slack >= 0 && MiningSafety.canStand(cell, budget) && !budget.exhausted()) {
                return new Plan(new ReflexAction.Flee(cell), seen.size());
            }
            if (slack > originSlack) {
                improving.add(new Visit(cell, slack));
            }
            for (int[] step : STEPS) {
                if (seen.size() < maxNodes) {
                    Optional<BlockCoord> next = move(cell, step[0], step[1], budget);
                    if (next.isPresent()
                            && withinRadius(origin, next.get(), maxRadius)
                            && keepsDistance(next.get(), threats, closestPass)
                            && seen.add(next.get())) {
                        queue.add(next.get());
                    }
                }
            }
        }
        improving.sort(Comparator.comparingDouble(Visit::slack).reversed());
        for (Visit visit : improving) {
            if (MiningSafety.canStand(visit.cell(), budget) && !budget.exhausted()) {
                return new Plan(new ReflexAction.Flee(visit.cell()), seen.size());
            }
        }
        return new Plan(new ReflexAction.StopMoving(), seen.size());
    }

    private record Visit(BlockCoord cell, double slack) {}

    private static double[] closestPass(BlockCoord origin, List<Threat> threats) {
        double[] limits = new double[threats.size()];
        for (int i = 0; i < limits.length; i++) {
            limits[i] = Math.min(CLOSEST_PASS, threats.get(i).distance(origin));
        }
        return limits;
    }

    private static boolean keepsDistance(BlockCoord cell, List<Threat> threats, double[] closestPass) {
        for (int i = 0; i < closestPass.length; i++) {
            if (threats.get(i).distance(cell) < closestPass[i]) {
                return false;
            }
        }
        return true;
    }

    private static double worstSlack(BlockCoord cell, List<Threat> threats) {
        double worst = Double.POSITIVE_INFINITY;
        for (Threat threat : threats) {
            worst = Math.min(worst, threat.slack(cell));
        }
        return worst;
    }

    private static boolean withinRadius(BlockCoord origin, BlockCoord cell, int radius) {
        return Math.abs(cell.x() - origin.x()) <= radius && Math.abs(cell.z() - origin.z()) <= radius;
    }

    private static Optional<BlockCoord> move(BlockCoord from, int dx, int dz, SafetyView view) {
        int x = from.x() + dx;
        int z = from.z() + dz;
        BlockCoord level = new BlockCoord(x, from.y(), z);
        Cell feet = view.cellAt(level);
        if (feet == Cell.SOLID) {
            return stepUp(from, level, view);
        }
        if (feet != Cell.PASSABLE || view.cellAt(new BlockCoord(x, from.y() + 1, z)) != Cell.PASSABLE) {
            return Optional.empty();
        }
        OptionalInt depth = DropPolicy.fallBelow(level, view);
        return depth.isPresent() ? Optional.of(new BlockCoord(x, from.y() - depth.getAsInt(), z)) : Optional.empty();
    }

    private static Optional<BlockCoord> stepUp(BlockCoord from, BlockCoord wall, SafetyView view) {
        BlockCoord above = new BlockCoord(wall.x(), wall.y() + 1, wall.z());
        BlockCoord twoAbove = new BlockCoord(wall.x(), wall.y() + 2, wall.z());
        BlockCoord headroom = new BlockCoord(from.x(), from.y() + 2, from.z());
        if (view.cellAt(above) == Cell.PASSABLE
                && view.cellAt(twoAbove) == Cell.PASSABLE
                && view.cellAt(headroom) == Cell.PASSABLE) {
            return Optional.of(above);
        }
        return Optional.empty();
    }

    private static final class BudgetView implements SafetyView {
        private final SafetyView source;
        private final int limit;
        private final Map<BlockCoord, Cell> memo = new HashMap<>();
        private boolean exhausted;

        BudgetView(SafetyView source, int limit) {
            this.source = source;
            this.limit = limit;
        }

        boolean exhausted() {
            return exhausted;
        }

        @Override
        public Cell cellAt(BlockCoord coord) {
            Cell known = memo.get(coord);
            if (known != null) {
                return known;
            }
            if (memo.size() >= limit) {
                exhausted = true;
                return Cell.SOLID;
            }
            Cell read = source.cellAt(coord);
            memo.put(coord, read);
            return read;
        }
    }
}
