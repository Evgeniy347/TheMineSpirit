package com.theminespirit.core.intellect;

public final class FoodDemand {
    private FoodDemand() {}

    public static double share(SettlementView view) {
        if (view == null) {
            throw new IllegalArgumentException("Спрос еды без обстановки не считается: зависит от запаса и числа душ");
        }
        IntellectTuning tuning = IntellectTuning.loadDefault();
        return view.foodDeficit(tuning.foodDays(), tuning.foodRation(), tuning.foodChildRation())
                .share();
    }

    public static double demand(ActivityId id, SettlementView view) {
        if (id == null) {
            throw new IllegalArgumentException("Спрос еды без деятельности не считается: id входит в план духа");
        }
        double share = share(view);
        if (share <= 0) {
            return Activity.checkedDemand(id, 0);
        }
        IntellectTuning tuning = IntellectTuning.loadDefault();
        double demand = tuning.foodDemandMin() + (tuning.foodDemandMax() - tuning.foodDemandMin()) * share;
        return Activity.checkedDemand(id, demand);
    }
}
