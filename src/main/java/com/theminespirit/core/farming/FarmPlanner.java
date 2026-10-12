package com.theminespirit.core.farming;

import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class FarmPlanner {
    public static final int MAX_SAFETY_READS_PER_TARGET = FarmStandSpots.MAX_SAFETY_READS;

    private static final int MAX_STAND_OFFSET_SQUARED = 11;

    private FarmPlanner() {
    }

    public static Outcome next(FarmJob job, CropKind filter, BlockCoord from, List<BlockCoord> candidates,
            FarmView view, SafetyView safety, Hydration hydration) {
        Objects.requireNonNull(job, "Работа не может быть null: без неё неизвестно, что проверять в клетке");
        Objects.requireNonNull(filter, "Фильтр вида не может быть null: «любой вид» задают CropKind.ANY");
        Objects.requireNonNull(from, "Точка духа не может быть null: по ней выбирается ближайшая цель");
        Objects.requireNonNull(candidates, "Список целей не может быть null: пустой набор задают пустым списком");
        Objects.requireNonNull(view, "FarmView не может быть null: по нему перечитывается клетка цели");
        Objects.requireNonNull(safety, "SafetyView не может быть null: по нему проверяется стоянка");
        Objects.requireNonNull(hydration, "Hydration не может быть null: по нему вспашка проверяет воду");
        List<BlockCoord> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparingLong((BlockCoord target) -> distanceSquared(from, target))
                .thenComparing(Comparator.naturalOrder()));
        CachedSafetyView cached = new CachedSafetyView(safety);
        Next best = null;
        long bestStandSquared = Long.MAX_VALUE;
        int ineligible = 0;
        int dry = 0;
        int noStand = 0;
        for (BlockCoord target : ordered) {
            if (best != null && cannotBeat(from, target, bestStandSquared)) {
                break;
            }
            FarmTile tile = view.tileAt(target);
            if (job == FarmJob.TILL && !job.eligible(tile, filter, true)) {
                ineligible++;
                continue;
            }
            boolean watered = job == FarmJob.TILL && hydration.watered(target);
            if (!job.eligible(tile, filter, watered)) {
                if (job == FarmJob.TILL && !watered) {
                    dry++;
                } else {
                    ineligible++;
                }
                continue;
            }
            List<BlockCoord> stands = FarmStandSpots.of(target, from, cached, view);
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
        return best == null ? new Exhausted(ineligible, noStand, dry) : best;
    }

    private static boolean cannotBeat(BlockCoord from, BlockCoord target, long bestStandSquared) {
        long targetSquared = distanceSquared(from, target);
        if (targetSquared <= MAX_STAND_OFFSET_SQUARED) {
            return false;
        }
        double gap = Math.sqrt(targetSquared) - Math.sqrt(MAX_STAND_OFFSET_SQUARED);
        return gap * gap > bestStandSquared + 1e-9;
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
            Objects.requireNonNull(target, "Цель не может быть null: без неё неизвестно, что обрабатывать");
            Objects.requireNonNull(stand, "Стоянка не может быть null: без неё неизвестно, куда идти");
        }
    }

    public record Exhausted(int ineligible, int noStand, int dry) implements Outcome {
        public Exhausted {
            if (ineligible < 0 || noStand < 0 || dry < 0) {
                throw new IllegalArgumentException("Счётчики пропущенных целей не могут быть отрицательными: ineligible="
                        + ineligible + ", noStand=" + noStand + ", dry=" + dry);
            }
        }
    }
}
