package com.theminespirit.core.intellect;

import com.theminespirit.core.coordinator.SpiritSnapshot;

public interface Activity {
    ActivityId id();

    double demand(SettlementView view);

    boolean eligible(SpiritSnapshot spirit, SettlementView view);

    ActivityIntent order(SpiritSnapshot spirit, SettlementView view);

    static double checkedDemand(ActivityId id, double demand) {
        double maxDemand = IntellectTuning.loadDefault().maxDemand();
        if (Double.isNaN(demand) || Double.isInfinite(demand) || demand < 0
                || demand > maxDemand) {
            throw new IllegalArgumentException("Спрос " + id + " = " + demand + ": обязан быть конечным числом в 0.."
                    + maxDemand + ", иначе распределение сравнивает несравнимое");
        }
        return demand;
    }
}
