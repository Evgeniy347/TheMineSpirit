package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.intellect.activity.ChopActivity;
import com.theminespirit.core.intellect.activity.DefendActivity;
import com.theminespirit.core.intellect.activity.FarmActivity;
import com.theminespirit.core.intellect.activity.MineActivity;
import com.theminespirit.core.intellect.activity.RestActivity;
import java.util.HashSet;
import java.util.List;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class DemandModelProperties {
    private static final ActivityCatalog CATALOG = ActivityCatalog.adultsOnly(List.of(
            new ChopActivity(), new FarmActivity(), new MineActivity(), new RestActivity(), new DefendActivity()));

    @Provide
    Arbitrary<SettlementView> views() {
        return SettlementViews.views();
    }

    @Property
    void evaluate_AnyView_SortedDescendingAndDeterministic(@ForAll("views") SettlementView view) {
        List<ActivityDemand> first = DemandModel.evaluate(view, CATALOG);
        List<ActivityDemand> second = DemandModel.evaluate(view, CATALOG);

        assertEquals(first, second, "Тот же вход даёт тот же спрос: иначе назначения мигают между пересборками");
        assertEquals(
                CATALOG.forChild(false).size(),
                first.size(),
                "Каждый id каталога встречается ровно раз: иначе часть работ не получит спроса");
        assertEquals(
                first.size(),
                new HashSet<>(first.stream().map(ActivityDemand::id).toList()).size(),
                "Повторов id в спросе нет: иначе одна деятельность займёт два места в очереди работ");
        for (int index = 1; index < first.size(); index++) {
            ActivityDemand prev = first.get(index - 1);
            ActivityDemand next = first.get(index);
            assertTrue(
                    prev.demand() > next.demand()
                            || (prev.demand() == next.demand() && prev.id().compareTo(next.id()) < 0),
                    "Спрос отсортирован по убыванию с тай-брейком по id: иначе выбор работы зависит от обхода");
        }
    }
}
