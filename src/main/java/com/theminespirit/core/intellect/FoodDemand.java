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
        return Activity.checkedDemand(id, IntellectTuning.FOOD_DEMAND_MAX * share(view));
    }
}
