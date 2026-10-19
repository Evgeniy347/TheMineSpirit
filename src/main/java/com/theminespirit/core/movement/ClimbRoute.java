package com.theminespirit.core.movement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ClimbRoute {
    private ClimbRoute() {}

    public record Span(int bottomY, int topY) {
        public Span {
            if (bottomY > topY) {
                throw new IllegalArgumentException("Низ колонны y=" + bottomY + " выше верха y=" + topY
                        + ": у вертикальной колонны низ не бывает выше верха");
            }
        }

        public int targetY(boolean down) {
            return down ? bottomY : topY;
        }

        public int distanceTo(int feetY) {
            if (feetY < bottomY) {
                return bottomY - feetY;
            }
            return feetY > topY ? feetY - topY : 0;
        }
    }

    public static Optional<Span> span(List<Integer> climbableYs, int feetY) {
        Objects.requireNonNull(
                climbableYs, "climbableYs не может быть null: колонна задаётся списком высот, " + "пустая — List.of()");
        List<Integer> sorted = climbableYs.stream().distinct().sorted().toList();
        if (sorted.isEmpty()) {
            return Optional.empty();
        }
        Span best = null;
        int runStart = 0;
        for (int i = 1; i <= sorted.size(); i++) {
            if (i < sorted.size() && sorted.get(i) == sorted.get(i - 1) + 1) {
                continue;
            }
            Span run = new Span(sorted.get(runStart), sorted.get(i - 1));
            if (best == null || closer(run, best, feetY)) {
                best = run;
            }
            runStart = i;
        }
        return Optional.ofNullable(best);
    }

    private static boolean closer(Span candidate, Span current, int feetY) {
        int candidateDistance = candidate.distanceTo(feetY);
        int currentDistance = current.distanceTo(feetY);
        if (candidateDistance != currentDistance) {
            return candidateDistance < currentDistance;
        }
        return candidate.bottomY() > current.bottomY();
    }

    public static List<Integer> range(int bottomY, int topY) {
        if (bottomY > topY) {
            throw new IllegalArgumentException("Диапазон высот [" + bottomY + ", " + topY + "] пуст: низ выше верха");
        }
        List<Integer> values = new ArrayList<>();
        for (int y = bottomY; y <= topY; y++) {
            values.add(y);
        }
        return values;
    }
}
