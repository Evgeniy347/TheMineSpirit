package com.theminespirit.core.movement;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

public final class Passage {
    public enum Kind {
        DOOR,
        FENCE_GATE
    }

    public enum Step {
        APPROACH,
        OPEN,
        PASS,
        CLOSE,
        DONE
    }

    public record State(boolean open, boolean withinReach, boolean passed) {}

    public record Cell(BlockCoord coord, Kind kind, boolean open) {
        public Cell {
            Objects.requireNonNull(coord, "������ ������� �� ����� ���� null: ����������, ��� ���������");
            Objects.requireNonNull(kind, "��� ������� �� ����� ���� null: ����� � ������� ����������� ��-�������");
        }

        public boolean passable() {
            return open;
        }

        public boolean routePassable() {
            return true;
        }
    }

    private Passage() {}

    public static Step next(State state) {
        if (state.passed()) {
            return state.open() ? Step.CLOSE : Step.DONE;
        }
        if (state.open()) {
            return Step.PASS;
        }
        return state.withinReach() ? Step.OPEN : Step.APPROACH;
    }

    public static Optional<Cell> towardTarget(BlockCoord feet, BlockCoord target, Collection<Cell> candidates) {
        Objects.requireNonNull(feet, "���� ���� �� ����� ���� null: ������ ���������� �� ���");
        Objects.requireNonNull(target, "���� �� ����� ���� null: ������ ���������� � ���");
        Objects.requireNonNull(candidates, "��������� �� ����� ���� null: ������ ������ ������� List.of()");
        long limit = distanceSquared(feet, target);
        Cell best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Cell cell : candidates) {
            if (cell.open()) {
                continue;
            }
            long distance = distanceSquared(cell.coord(), target);
            if (distance >= limit) {
                continue;
            }
            if (best == null
                    || distance < bestDistance
                    || (distance == bestDistance && cell.coord().compareTo(best.coord()) < 0)) {
                best = cell;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    public static boolean blockedBetween(BlockCoord from, BlockCoord to, Collection<Cell> passages) {
        Objects.requireNonNull(from, "������ �������� �� ����� ���� null: ��� ���� ���������� �������");
        Objects.requireNonNull(to, "����� �������� �� ����� ���� null: ��� ���� ���������� �������");
        Objects.requireNonNull(passages, "������� �� ����� ���� null: ������ ������ ������� List.of()");
        for (Cell passage : passages) {
            if (!passage.open() && intersects(from, to, passage.coord())) {
                return true;
            }
        }
        return false;
    }

    private static boolean intersects(BlockCoord from, BlockCoord to, BlockCoord cell) {
        double[] start = {from.x() + 0.5, from.y() + 0.5, from.z() + 0.5};
        double[] end = {to.x() + 0.5, to.y() + 0.5, to.z() + 0.5};
        double[] low = {cell.x(), cell.y(), cell.z()};
        double[] high = {cell.x() + 1.0, cell.y() + 1.0, cell.z() + 1.0};
        double enter = 0.0;
        double exit = 1.0;
        for (int axis = 0; axis < 3; axis++) {
            double direction = end[axis] - start[axis];
            if (Math.abs(direction) < 1e-9) {
                if (start[axis] < low[axis] || start[axis] > high[axis]) {
                    return false;
                }
                continue;
            }
            double first = (low[axis] - start[axis]) / direction;
            double second = (high[axis] - start[axis]) / direction;
            if (first > second) {
                double swap = first;
                first = second;
                second = swap;
            }
            enter = Math.max(enter, first);
            exit = Math.min(exit, second);
            if (enter > exit) {
                return false;
            }
        }
        return true;
    }

    private static long distanceSquared(BlockCoord a, BlockCoord b) {
        long dx = (long) a.x() - b.x();
        long dy = (long) a.y() - b.y();
        long dz = (long) a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
