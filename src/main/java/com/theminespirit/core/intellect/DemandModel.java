package com.theminespirit.core.intellect;

import java.util.Comparator;
import java.util.List;

public final class DemandModel {
    private DemandModel() {}

    public static List<ActivityDemand> evaluate(SettlementView view, ActivityCatalog catalog) {
        if (view == null) {
            throw new IllegalArgumentException("Спрос без обстановки не считается: зависит от запасов и угроз");
        }
        if (catalog == null) {
            throw new IllegalArgumentException("Спрос без каталога не считается: деятельности — классы каталога");
        }
        return catalog.ordered().stream()
                .map(activity -> new ActivityDemand(activity.id(), activity.demand(view)))
                .sorted(Comparator.comparingDouble(ActivityDemand::demand)
                        .reversed()
                        .thenComparing(ActivityDemand::id))
                .toList();
    }
}
