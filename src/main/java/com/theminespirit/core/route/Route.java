package com.theminespirit.core.route;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Objects;

public record Route(Status status, List<RouteStep> steps, double cost, BlockCoord end, int nodes, int reads) {
    public enum Status {
        FOUND,
        PARTIAL,
        NONE
    }

    public Route {
        Objects.requireNonNull(status, "Итог поиска не может быть null");
        Objects.requireNonNull(end, "Конечная клетка не может быть null: от неё продолжается следующий расчёт");
        steps = List.copyOf(steps);
    }

    public int count(RouteStep.Action action) {
        int count = 0;
        for (RouteStep step : steps) {
            if (step.action() == action) {
                count++;
            }
        }
        return count;
    }

    public int places() {
        return count(RouteStep.Action.PLACE) + count(RouteStep.Action.PILLAR);
    }

    public boolean usable() {
        return status != Status.NONE && !steps.isEmpty();
    }
}
