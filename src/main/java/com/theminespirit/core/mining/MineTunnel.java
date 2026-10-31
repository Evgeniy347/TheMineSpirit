package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class MineTunnel {
    public static final int MAX_STEPS = 16;
    private static final int MAX_REACH_SIDE = 3;
    private static final int MAX_REACH_DOWN = 3;
    private static final int MAX_REACH_UP = 15;
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[] RISES = {-1, 0, 1};
    private static final Set<Cell> DIGGABLE = Set.of(Cell.SOLID, Cell.FALLING);

    public record Step(BlockCoord stand, List<BlockCoord> dig) {
        public Step {
            Objects.requireNonNull(stand, "Стоянка шага не может быть null: дух встаёт именно на неё");
            dig = List.copyOf(Objects.requireNonNull(dig, "Клетки ломки не могут быть null: пустой список"));
        }
    }

    public record Plan(BlockCoord target, List<Step> steps, String refusal) {
        public Plan {
            Objects.requireNonNull(target, "Цель прохода не может быть null: без неё неизвестно, куда копать");
            steps = List.copyOf(Objects.requireNonNull(steps, "Шаги прохода не могут быть null: пустой список"));
            Objects.requireNonNull(refusal, "Причина отказа не может быть null: пустая строка — проход есть");
        }

        public boolean found() {
            return refusal.isEmpty();
        }
    }

    private record Link(BlockCoord from, Step step) {}

    private record Open(BlockCoord stand, int steps, long estimate) {}

    private MineTunnel() {}

    public static Plan plan(BlockCoord target, BlockCoord feet, SafetyView view, int maxSteps) {
        Objects.requireNonNull(target, "Цель прохода не может быть null: без неё неизвестно, куда копать");
        Objects.requireNonNull(feet, "Точка духа не может быть null: проход начинается от ног");
        Objects.requireNonNull(view, "Вид мира не может быть null: по нему проверяется порода на пути");
        if (maxSteps < 1) {
            throw new IllegalArgumentException(
                    "Предел шагов " + maxSteps + " меньше единицы: проход не копают на " + "ноль шагов");
        }
        return new Search(target, feet, view, maxSteps).run();
    }

    private static final class Search {
        private final BlockCoord target;
        private final BlockCoord feet;
        private final SafetyView view;
        private final int maxSteps;
        private final Map<BlockCoord, Link> parents = new HashMap<>();
        private final Map<BlockCoord, Integer> best = new HashMap<>();
        private final Queue<Open> queue =
                new PriorityQueue<>(Comparator.comparingLong(Open::estimate).thenComparingInt(Open::steps));
        private boolean unsafeSeen;

        private Search(BlockCoord target, BlockCoord feet, SafetyView view, int maxSteps) {
            this.target = target;
            this.feet = feet;
            this.view = view;
            this.maxSteps = maxSteps;
        }

        private Plan run() {
            best.put(feet, 0);
            queue.add(new Open(feet, 0, estimate(feet, target)));
            while (!queue.isEmpty()) {
                Open open = queue.poll();
                if (open.steps() > best.getOrDefault(open.stand(), Integer.MAX_VALUE)) {
                    continue;
                }
                List<BlockCoord> opened = openedTo(open.stand(), parents);
                SafetyView virtual = opened(view, opened);
                if (sees(open.stand(), target, opened, virtual)) {
                    return new Plan(target, stepsTo(open.stand(), parents), "");
                }
                if (open.steps() < maxSteps) {
                    expand(open, opened);
                }
            }
            String refusal = "к руде " + target + " нет прохода в пределах " + maxSteps + " шагов";
            if (unsafeSeen) {
                refusal += ": на пути жидкость или опасный блок";
            }
            return new Plan(target, List.of(), refusal);
        }

        private void expand(Open open, List<BlockCoord> opened) {
            for (int[] side : SIDES) {
                for (int rise : RISES) {
                    BlockCoord next = new BlockCoord(
                            open.stand().x() + side[0],
                            open.stand().y() + rise,
                            open.stand().z() + side[1]);
                    if (!inside(next) || next.equals(target)) {
                        continue;
                    }
                    List<BlockCoord> dig = digCells(next, view);
                    if (dig == null) {
                        unsafeSeen = true;
                        continue;
                    }
                    List<BlockCoord> openedNext = new ArrayList<>(opened);
                    openedNext.addAll(dig);
                    SafetyView virtual = opened(view, openedNext);
                    boolean digSafe = canDigSafely(dig, virtual);
                    if (!digSafe || !MiningSafety.canStand(next, virtual)) {
                        unsafeSeen = unsafeSeen || !digSafe;
                        continue;
                    }
                    int steps = open.steps() + 1;
                    if (steps >= best.getOrDefault(next, Integer.MAX_VALUE)) {
                        continue;
                    }
                    best.put(next, steps);
                    parents.put(next, new Link(open.stand(), new Step(next, dig)));
                    queue.add(new Open(next, steps, steps + estimate(next, target)));
                }
            }
        }

        private boolean inside(BlockCoord cell) {
            int reach = maxSteps + MAX_REACH_SIDE;
            return Math.abs(cell.x() - feet.x()) <= reach
                    && Math.abs(cell.z() - feet.z()) <= reach
                    && cell.y() >= feet.y() - reach
                    && cell.y() <= feet.y() + 2;
        }

        private List<BlockCoord> openedTo(BlockCoord stand, Map<BlockCoord, Link> links) {
            List<BlockCoord> cells = new ArrayList<>();
            BlockCoord at = stand;
            while (true) {
                Link link = links.get(at);
                if (link == null) {
                    return cells;
                }
                cells.addAll(link.step().dig());
                at = link.from();
            }
        }

        private List<Step> stepsTo(BlockCoord stand, Map<BlockCoord, Link> links) {
            List<Step> steps = new ArrayList<>();
            BlockCoord at = stand;
            while (true) {
                Link link = links.get(at);
                if (link == null) {
                    Collections.reverse(steps);
                    return List.copyOf(steps);
                }
                steps.add(link.step());
                at = link.from();
            }
        }
    }

    private static @Nullable List<BlockCoord> digCells(BlockCoord stand, SafetyView view) {
        List<BlockCoord> dig = new ArrayList<>(2);
        for (int height = 0; height <= 1; height++) {
            BlockCoord cell = new BlockCoord(stand.x(), stand.y() + height, stand.z());
            Cell kind = view.cellAt(cell);
            if (kind == Cell.PASSABLE) {
                continue;
            }
            if (!DIGGABLE.contains(kind)) {
                return null;
            }
            dig.add(cell);
        }
        BlockCoord floor = new BlockCoord(stand.x(), stand.y() - 1, stand.z());
        Cell floorKind = view.cellAt(floor);
        if (floorKind != Cell.SOLID && floorKind != Cell.FALLING) {
            return null;
        }
        return dig;
    }

    private static boolean canDigSafely(List<BlockCoord> dig, SafetyView view) {
        for (BlockCoord cell : dig) {
            if (!(MiningSafety.canBreak(cell, view) instanceof BreakSafety.Safe)) {
                return false;
            }
        }
        return true;
    }

    private static boolean sees(BlockCoord stand, BlockCoord target, List<BlockCoord> opened, SafetyView view) {
        if (stand.x() == target.x() && stand.z() == target.z()) {
            return false;
        }
        int dx = Math.abs(target.x() - stand.x());
        int dz = Math.abs(target.z() - stand.z());
        int dy = target.y() - stand.y();
        if (dx > MAX_REACH_SIDE || dz > MAX_REACH_SIDE || dy > MAX_REACH_UP || dy < -MAX_REACH_DOWN) {
            return false;
        }
        if (!(MiningSafety.canBreak(target, view) instanceof BreakSafety.Safe)) {
            return false;
        }
        return MineDigOrder.visible(stand, target, opened, view);
    }

    private static SafetyView opened(SafetyView view, List<BlockCoord> cut) {
        Set<BlockCoord> cells = new HashSet<>(cut);
        return coord -> cells.contains(coord) ? Cell.PASSABLE : view.cellAt(coord);
    }

    private static long estimate(BlockCoord from, BlockCoord target) {
        return Math.abs((long) target.x() - from.x())
                + Math.abs((long) target.y() - from.y())
                + Math.abs((long) target.z() - from.z());
    }
}
