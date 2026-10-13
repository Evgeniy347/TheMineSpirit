package com.theminespirit.core.intellect.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.intellect.Activity;
import com.theminespirit.core.intellect.SettlementView;
import com.theminespirit.core.intellect.SettlementViews;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class ActivitiesProperties {
    private static final List<Activity> ALL = List.of(new ChopActivity(), new FarmActivity(), new MineActivity(),
            new RestActivity(), new DefendActivity());

    @Provide
    Arbitrary<SettlementView> views() {
        return SettlementViews.views();
    }

    @Property
    void demand_EvaluationOrderChanged_SameValues(@ForAll("views") SettlementView view) {
        List<Activity> forward = new ArrayList<>(ALL);
        List<Activity> backward = new ArrayList<>(ALL);
        Collections.reverse(backward);

        Map<String, Double> first = demandsInOrder(forward, view);
        Map<String, Double> second = demandsInOrder(backward, view);
        Map<String, Double> third = demandsInOrder(forward, view);

        assertEquals(first, second,
                "Спрос не зависит от порядка вычисления других деятельностей: иначе итог зависит от обхода каталога");
        assertEquals(first, third,
                "Повторный расчёт даёт тот же спрос: иначе спрос мигает между пересборками");
    }

    private static Map<String, Double> demandsInOrder(List<Activity> order, SettlementView view) {
        Map<String, Double> demands = new HashMap<>();
        for (Activity activity : order) {
            demands.put(activity.id().name(), activity.demand(view));
        }
        return demands;
    }
}
