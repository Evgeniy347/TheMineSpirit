package com.theminespirit.core.coordinator;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WorkAllocator {
    public WorkAllocator() {
    }

    public List<WorkAssignment> allocate(List<SpiritSnapshot> free, List<WorkTarget> candidates,
            WorkReservations reservations, long now) {
        if (free == null) {
            throw new IllegalArgumentException("free не может быть null: нет свободных — пустой список");
        }
        if (candidates == null) {
            throw new IllegalArgumentException("candidates не может быть null: нет целей — пустой список");
        }
        if (reservations == null) {
            throw new IllegalArgumentException("reservations не может быть null: брони без реестра не проверить");
        }
        if (now < 0) {
            throw new IllegalArgumentException("now = " + now + ": время распределения не может быть отрицательным");
        }
        reservations.expire(now);
        List<WorkTarget> freeTargets = candidates.stream()
                .filter(target -> reservations.owner(target).isEmpty())
                .toList();
        Map<UUID, SpiritSnapshot> byId = new HashMap<>();
        for (SpiritSnapshot spirit : free) {
            byId.putIfAbsent(spirit.id(), spirit);
        }
        Map<WorkTarget, Long> assigned = new HashMap<>();
        List<Planned> planned = new ArrayList<>();
        for (UUID spiritId : byId.keySet().stream().sorted().toList()) {
            SpiritSnapshot spirit = byId.get(spiritId);
            WorkTarget best = null;
            double bestScore = Double.POSITIVE_INFINITY;
            for (WorkTarget target : freeTargets) {
                double score = spirit.pos().distanceTo(anchorOf(target))
                        + CoordinatorTuning.CROWD_PENALTY * assigned.getOrDefault(target, 0L);
                if (score < bestScore || (score == bestScore && best != null
                        && target.anchor().compareTo(best.anchor()) < 0)) {
                    best = target;
                    bestScore = score;
                }
            }
            if (best != null) {
                planned.add(new Planned(new WorkAssignment(spiritId, best), bestScore));
                assigned.merge(best, 1L, Long::sum);
            }
        }
        return planned.stream()
                .sorted(Comparator.comparingDouble((Planned plan) -> plan.score())
                        .thenComparing(plan -> plan.assignment().target().anchor())
                        .thenComparing(plan -> plan.assignment().spirit()))
                .map(Planned::assignment)
                .toList();
    }

    private static Point anchorOf(WorkTarget target) {
        BlockCoord anchor = target.anchor();
        return new Point(anchor.x(), anchor.y(), anchor.z());
    }

    private record Planned(WorkAssignment assignment, double score) {
    }
}
