package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public final class MiningPlanner {

    private MiningPlanner() {}

    public static Outcome next(BlockCoord from, List<BlockCoord> remaining, SafetyView view) {
        return next(from, remaining, view, Set.of());
    }

    public static Outcome next(BlockCoord from, List<BlockCoord> remaining, SafetyView view, Set<BlockCoord> excluded) {
        return next(from, remaining, view, excluded, stand -> true);
    }

    public static Outcome next(
            BlockCoord from,
            List<BlockCoord> remaining,
            SafetyView view,
            Set<BlockCoord> excluded,
            Predicate<BlockCoord> reachable) {
        return plan(from, remaining, view, excluded, reachable, false);
    }

    public static Outcome nextReachable(
            BlockCoord from,
            List<BlockCoord> remaining,
            SafetyView view,
            Set<BlockCoord> excluded,
            Predicate<BlockCoord> reachable) {
        Outcome strict = plan(from, remaining, view, excluded, reachable, true);
        if (strict instanceof Next) {
            return strict;
        }
        return plan(from, remaining, view, excluded, stand -> true, false);
    }

    private static Outcome plan(
            BlockCoord from,
            List<BlockCoord> remaining,
            SafetyView view,
            Set<BlockCoord> excluded,
            Predicate<BlockCoord> reachable,
            boolean pathChecked) {
        Objects.requireNonNull(from, "Точка духа не может быть null: по ней выбирается ближайшая цель");
        Objects.requireNonNull(remaining, "Список целей не может быть null: пустой набор задают пустым списком");
        Objects.requireNonNull(view, "SafetyView не может быть null: по нему проверяется безопасность цели");
        Objects.requireNonNull(
                excluded, "Множество исключённых стоянок не может быть null: без него непонятно, что пропускать");
        Objects.requireNonNull(
                reachable,
                "Проверка пути к стоянке не может быть null: без неё выбор цели снова возьмёт "
                        + "блок, к которому дух не подойдёт");
        List<BlockCoord> ordered = new ArrayList<>(remaining);
        ordered.sort(Comparator.comparingLong((BlockCoord target) -> distanceSquared(from, target))
                .thenComparing(Comparator.naturalOrder()));
        Next best = null;
        long bestStandSquared = Long.MAX_VALUE;
        int unsafe = 0;
        int noStand = 0;
        int unreachable = 0;
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
            List<BlockCoord> open = new ArrayList<>(stands);
            if (!pathChecked) {
                open.removeIf(stand -> !StandSpots.walkable(stand, from));
            }
            open.removeIf(excluded::contains);
            BlockCoord chosen = null;
            for (BlockCoord stand : open) {
                if (reachable.test(stand)) {
                    chosen = stand;
                    break;
                }
            }
            if (chosen == null) {
                unreachable++;
                continue;
            }
            Next candidate = new Next(target, chosen);
            if (best == null || closer(candidate, best, from)) {
                best = candidate;
                bestStandSquared = distanceSquared(from, candidate.stand());
            }
        }
        return best == null ? new Exhausted(unsafe, noStand, unreachable) : best;
    }

    private static boolean cannotBeat(BlockCoord from, BlockCoord target, long bestStandSquared) {
        long targetSquared = distanceSquared(from, target);
        if (targetSquared <= StandSpots.MAX_OFFSET_SQUARED) {
            return false;
        }
        double gap = Math.sqrt((double) targetSquared) - Math.sqrt(StandSpots.MAX_OFFSET_SQUARED);
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

    public sealed interface Outcome permits Next, Exhausted {}

    public record Next(BlockCoord target, BlockCoord stand) implements Outcome {
        public Next {
            Objects.requireNonNull(target, "Цель не может быть null: без неё неизвестно, что добывать");
            Objects.requireNonNull(stand, "Стоянка не может быть null: без неё неизвестно, куда идти");
        }
    }

    public record Exhausted(int unsafe, int noStand, int unreachable) implements Outcome {
        public Exhausted {
            if (unsafe < 0 || noStand < 0 || unreachable < 0) {
                throw new IllegalArgumentException("Счётчики пропущенных целей не могут быть отрицательными: unsafe="
                        + unsafe + ", noStand=" + noStand + ", unreachable=" + unreachable);
            }
        }
    }
}
