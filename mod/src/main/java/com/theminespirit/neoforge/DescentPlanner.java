package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildReservations;
import com.theminespirit.core.mining.DescentPlan;
import com.theminespirit.core.mining.DescentStep;
import com.theminespirit.core.observation.BlockCoord;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

final class DescentPlanner {
    private DescentPlanner() {}

    static @Nullable String check(
            DescentStep step, DescentPlan plan, LevelSafetyView safety, BuildReservations reservations, UUID spirit) {
        String danger = DescentPlan.dangerAt(safety, step, plan.direction());
        if (danger != null) {
            return danger;
        }
        for (BlockCoord cell : step.passage()) {
            if (reservations.foreign(cell, spirit)) {
                return "клетка " + cell + " занята другим духом: спуск остановлен";
            }
        }
        for (BlockCoord cell : step.passage()) {
            reservations.reserve(cell, spirit);
        }
        return null;
    }
}
