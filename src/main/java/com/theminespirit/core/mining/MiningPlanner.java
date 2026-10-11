package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class MiningPlanner {
    public static final int MAX_VIEW_READS_PER_TARGET = MiningSafety.FACE_READS + StandSpots.MAX_VIEW_READS;

    private static final int STAND_RADIUS = 3;

    private MiningPlanner() {
    }

    public static Outcome next(BlockCoord from, List<BlockCoord> remaining, SafetyView view) {
        Objects.requireNonNull(from, "Точка духа не может быть null: по ней выбирается ближайшая цель");
        Objects.requireNonNull(remaining, "Список целей не может быть null: пустой набор задают пустым списком");
        Objects.requireNonNull(view, "SafetyView не может быть null: по нему проверяется безопасность цели");
        List<BlockCoord> ordered = new ArrayList<>(remaining);
        ordered.sort(Comparator.comparingLong((BlockCoord target) -> distanceSquared(from, target))
                .thenComparing(Comparator.naturalOrder()));
        Next best = null;
        long bestStandSquared = Long.MAX_VALUE;
        int unsafe = 0;
        int noStand = 0;
        for (BlockCoord target : ordered) {
            if (best != null && cannotBeat(from, target, bestStandSquared)) {
                break;
            }
            if (!(MiningSafety.canBreak(target, view) instanceof BreakSafety.Safe)) {
                unsafe++;
                continue;
            }
            List<BlockCoord> stands = StandSpots.of(target, view, from);
            if (stands.isEmpty()) {
                noStand++;
                continue;
            }
            Next candidate = new Next(target, stands.get(0));
            if (best == null || closer(candidate, best, from)) {
                best = candidate;
                bestStandSquared = distanceSquared(from, candidate.stand());
            }
        }
        return best == null ? new Exhausted(unsafe, noStand) : best;
    }

    private static boolean cannotBeat(BlockCoord from, BlockCoord target, long bestStandSquared) {
        long targetSquared = distanceSquared(from, target);
        if (targetSquared <= (long) STAND_RADIUS * STAND_RADIUS) {
            return false;
        }
        double gap = Math.sqrt(targetSquared) - STAND_RADIUS;
        return gap * gap > bestStandSquared;
    }

    private static boolean closer(Next candidate, Next current, BlockCoord from) {
        long candidateDistance = distanceSquared(from, candidate.stand());
        long currentDistance = distanceSquared(from, current.stand());
        if (candidateDistance != currentDistance) {
            return candidateDistance < currentDistance;
        }
        int byTarget = candidate.target().compareTo(current.target());
        if (byTarget != 0) {
            return byTarget < 0;
        }
        return candidate.stand().compareTo(current.stand()) < 0;
    }

    private static long distanceSquared(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        return dx * dx + dy * dy + dz * dz;
    }

    public sealed interface Outcome permits Next, Exhausted {
    }

    public record Next(BlockCoord target, BlockCoord stand) implements Outcome {
        public Next {
            Objects.requireNonNull(target, "Цель не может быть null: без неё неизвестно, что добывать");
            Objects.requireNonNull(stand, "Стоянка не может быть null: без неё неизвестно, куда идти");
        }
    }

    public record Exhausted(int unsafe, int noStand) implements Outcome {
        public Exhausted {
            if (unsafe < 0 || noStand < 0) {
                throw new IllegalArgumentException("Счётчики пропущенных целей не могут быть отрицательными: unsafe="
                        + unsafe + ", noStand=" + noStand);
            }
        }
    }
}
