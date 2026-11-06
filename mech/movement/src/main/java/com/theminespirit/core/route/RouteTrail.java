package com.theminespirit.core.route;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public final class RouteTrail {
    private final int keep;
    private final Deque<RouteStep> steps = new ArrayDeque<>();

    public RouteTrail(int keep) {
        if (keep < 1) {
            throw new IllegalArgumentException(
                    "Длина истории маршрута " + keep + " не положительна: без шагов смерть духа не объяснить");
        }
        this.keep = keep;
    }

    public void add(RouteStep step) {
        Objects.requireNonNull(step, "Шаг маршрута не может быть null: история хранит только действия духа");
        steps.addLast(step);
        while (steps.size() > keep) {
            steps.removeFirst();
        }
    }

    public List<RouteStep> last() {
        return List.copyOf(steps);
    }
}
