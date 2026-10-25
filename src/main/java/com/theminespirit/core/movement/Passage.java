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
            Objects.requireNonNull(coord, "Клетка прохода не может быть null: неизвестно, что открывать");
            Objects.requireNonNull(kind, "Вид прохода не может быть null: дверь и калитка открываются по-разному");
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
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: проход выбирается от них");
        Objects.requireNonNull(target, "Цель не может быть null: проход выбирается к ней");
        Objects.requireNonNull(candidates, "Кандидаты не могут быть null: пустой список задаётся List.of()");
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

    private static long distanceSquared(BlockCoord a, BlockCoord b) {
        long dx = (long) a.x() - b.x();
        long dy = (long) a.y() - b.y();
        long dz = (long) a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
