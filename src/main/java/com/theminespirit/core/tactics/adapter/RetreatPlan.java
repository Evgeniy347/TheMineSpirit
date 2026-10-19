package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.tactics.Point;

public sealed interface RetreatPlan {
    record Walk(Point target, double arrival, boolean shooting) implements RetreatPlan {
        public Walk {
            if (target == null || arrival <= 0) {
                throw new IllegalArgumentException(
                        "RetreatPlan.Walk: цель обязательна, дистанция прибытия больше нуля; " + "получено target = "
                                + target + ", arrival = " + arrival);
            }
        }
    }

    /** Вылить ведро воды под себя: MLG при падении и тушение огня, найденной воды рядом нет. */
    record Pour() implements RetreatPlan {}
}
