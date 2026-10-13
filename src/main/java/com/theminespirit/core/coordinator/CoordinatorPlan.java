package com.theminespirit.core.coordinator;

import com.theminespirit.core.tactics.Point;
import java.util.List;
import java.util.Optional;

public record CoordinatorPlan(Optional<Point> defendedPoint, double defendedRadius, List<DefenseAssignment> defense,
        List<AssistOrder> assists, List<WorkAssignment> work) {

    public CoordinatorPlan {
        if (defendedPoint == null) {
            throw new IllegalArgumentException(
                    "defendedPoint не может быть null: нет точки обороны — передайте Optional.empty()");
        }
        if (defendedRadius < 0) {
            throw new IllegalArgumentException(
                    "defendedRadius = " + defendedRadius + ": радиус обороны не может быть отрицательным");
        }
        defense = List.copyOf(defense);
        assists = List.copyOf(assists);
        work = List.copyOf(work);
    }
}
