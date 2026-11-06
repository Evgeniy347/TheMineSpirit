package com.theminespirit.core.board;

import com.theminespirit.core.plan.TaskNode;
import java.util.Objects;

public final class TicketPlanPick {
    private TicketPlanPick() {}

    public static TaskNode plan(BoardCase boardCase, int level, TicketTarget target) {
        Objects.requireNonNull(boardCase, "Кейс задания не может быть null: по нему выбирается план");
        Objects.requireNonNull(target, "Цель задания не может быть null: из неё собирается план");
        if (target.plan() != null) {
            return target.plan();
        }
        return TicketPlans.plan(boardCase, level, target);
    }
}
