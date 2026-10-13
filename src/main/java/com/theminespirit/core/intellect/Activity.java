package com.theminespirit.core.intellect;

import com.theminespirit.core.coordinator.SpiritSnapshot;

public interface Activity {
    double MAX_DEMAND = 10.0;

    ActivityId id();

    double demand(SettlementView view);

    boolean eligible(SpiritSnapshot spirit, SettlementView view);

    ActivityIntent order(SpiritSnapshot spirit, SettlementView view);

    static double checkedDemand(ActivityId id, double demand) {
        if (Double.isNaN(demand) || Double.isInfinite(demand) || demand < 0 || demand > MAX_DEMAND) {
            throw new IllegalArgumentException("Спрос " + id + " = " + demand + ": обязан быть конечным числом в 0.."
                    + MAX_DEMAND + ", иначе распределение сравнивает несравнимое");
        }
        return demand;
    }
}
