package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.intellect.activity.ChopActivity;
import com.theminespirit.core.intellect.activity.DefendActivity;
import com.theminespirit.core.intellect.activity.FarmActivity;
import com.theminespirit.core.intellect.activity.MineActivity;
import com.theminespirit.core.intellect.activity.RestActivity;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DemandModelTests {
    private static final ActivityCatalog CATALOG = ActivityCatalog.adultsOnly(
            List.of(new ChopActivity(), new FarmActivity(), new MineActivity(), new RestActivity(),
                    new DefendActivity()));

    @Test
    void evaluate_ThreatsPresent_DefendFirst() {
        List<ActivityDemand> demands =
                DemandModel.evaluate(SettlementViews.settlement(0, 0, 0, 0, 0, 2, 0.2), CATALOG);

        assertEquals(ActivityId.DEFEND, demands.get(0).id(),
                "При угрозе защита первая в спросе: иначе распределитель раздаст работу вместо обороны");
        assertEquals(10.0, demands.get(0).demand(),
                "Спрос защиты при угрозе — максимум шкалы: иначе голод или руда перебьют оборону");
    }

    @Test
    void evaluate_ValuesMatch_ActivityDemands() {
        SettlementView view = SettlementViews.settlement(2, 2, 64, 32, 2, 0, 0.2);
        List<ActivityDemand> demands = DemandModel.evaluate(view, CATALOG);

        Map<ActivityId, Double> expected = Map.of(ActivityId.CHOP, 3.0, ActivityId.FARM, 1.0, ActivityId.MINE, 1.0,
                ActivityId.REST, 4.5, ActivityId.DEFEND, 0.5);
        for (ActivityDemand demand : demands) {
            assertEquals(expected.get(demand.id()), demand.demand(),
                    "Спрос " + demand.id() + " совпадает с деятельностью: иначе сортируется чужое число");
        }
        assertEquals(List.of(ActivityId.REST, ActivityId.CHOP, ActivityId.FARM, ActivityId.MINE, ActivityId.DEFEND),
                demands.stream().map(ActivityDemand::id).toList(),
                "Порядок спроса при сытости: отдых, рубка, равные ферма и добыча по id, защита последняя");
    }

    @Test
    void evaluate_EmptyCatalog_ReturnsEmpty() {
        assertEquals(List.of(),
                DemandModel.evaluate(SettlementViews.settlement(2, 2, 64, 32, 2, 0, 0.2),
                        ActivityCatalog.adultsOnly(List.of())),
                "Пустой детский каталог даёт пустой спрос: иначе ребёнок получит взрослую работу");
    }

    @Test
    void evaluate_NullInputs_Throw() {
        assertThrows(IllegalArgumentException.class, () -> DemandModel.evaluate(null, CATALOG),
                "Спрос без обстановки не считается: коэффициент зависит от запасов и угроз");
        assertThrows(IllegalArgumentException.class,
                () -> DemandModel.evaluate(SettlementViews.settlement(2, 2, 64, 32, 2, 0, 0.2), null),
                "Спрос без каталога не считается: деятельности — классы каталога, а не ветки");
    }

    @Test
    void demand_BadValues_Throw() {
        assertThrows(IllegalArgumentException.class, () -> new ActivityDemand(null, 1),
                "Спрос без деятельности не распределяется: id входит в план духа");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ActivityDemand(ActivityId.CHOP, -2),
                "Спрос вне шкалы ломает сортировку: распределение выберет не ту работу");

        assertTrue(error.getMessage().contains("CHOP"),
                "Сообщение называет деятельность: при падении видно, чей спрос вышел из диапазона");
    }
}
