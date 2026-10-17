package com.theminespirit.core.intellect;

public final class FoodDemand {
    private FoodDemand() {
    }

    public static double share(SettlementView view) {
        if (view == null) {
            throw new IllegalArgumentException(
                    "Спрос еды без обстановки не считается: зависит от запаса и числа душ");
        }
        return view.foodDeficit(IntellectTuning.FOOD_DAYS, IntellectTuning.FOOD_RATION,
                IntellectTuning.FOOD_CHILD_RATION).share();
    }

    public static double demand(ActivityId id, SettlementView view) {
        if (id == null) {
            throw new IllegalArgumentException("Спрос еды без деятельности не считается: id входит в план духа");
        }
        double share = share(view);
        if (share <= 0) {
            return Activity.checkedDemand(id, 0);
        }
        double demand = IntellectTuning.FOOD_DEMAND_MIN
                + (IntellectTuning.FOOD_DEMAND_MAX - IntellectTuning.FOOD_DEMAND_MIN) * share;
        return Activity.checkedDemand(id, demand);
    }
}
