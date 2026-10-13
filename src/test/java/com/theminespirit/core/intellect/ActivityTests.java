package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.coordinator.WorkKind;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ActivityTests {
    private static final class FixedActivity implements Activity {
        private final ActivityId activityId;
        private final double demandValue;

        FixedActivity(ActivityId activityId, double demandValue) {
            this.activityId = activityId;
            this.demandValue = demandValue;
        }

        @Override
        public ActivityId id() {
            return activityId;
        }

        @Override
        public double demand(SettlementView view) {
            if (view == null) {
                throw new IllegalArgumentException("Деятельность " + activityId
                        + " без обстановки не считается: спрос зависит от запасов и угроз");
            }
            return Activity.checkedDemand(activityId, demandValue);
        }

        @Override
        public boolean eligible(SpiritSnapshot spirit, SettlementView view) {
            if (spirit == null || view == null) {
                throw new IllegalArgumentException("Пригодность " + activityId
                        + " без духа или обстановки не проверяется");
            }
            return true;
        }

        @Override
        public ActivityIntent order(SpiritSnapshot spirit, SettlementView view) {
            if (spirit == null || view == null) {
                throw new IllegalArgumentException(
                        "Намерение " + activityId + " без духа или обстановки не строится");
            }
            return new ActivityIntent(activityId, Optional.of("wait"), Map.of(), Optional.empty());
        }
    }

    @Test
    void intent_ConstructorArgs_AreExposed() {
        ActivityIntent intent = new ActivityIntent(ActivityId.CHOP, Optional.of("chop_tree"),
                Map.of("radius", 8), Optional.of(WorkKind.TREE));

        assertEquals(ActivityId.CHOP, intent.id(), "id намерения — какая деятельность: план духа строится по нему");
        assertEquals(Optional.of("chop_tree"), intent.script(),
                "Скрипт намерения — чем исполнять: пусто только у деятельности координатора");
        assertEquals(Map.of("radius", 8), intent.params(),
                "Параметры намерения — с чем исполнять: радиус рубки уходит в скрипт");
        assertEquals(Optional.of(WorkKind.TREE), intent.workKind(),
                "Вид брони намерения — что занимать в координаторе: дерево бронируется видом TREE");
    }

    @Test
    void intent_BlankScript_Throws() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ActivityIntent(ActivityId.CHOP, Optional.of("  "), Map.of(), Optional.empty()),
                "Намерение с пробелами вместо скрипта не исполнить: каталог скриптов такое имя не найдёт");

        assertTrue(error.getMessage().contains("CHOP"),
                "Сообщение называет деятельность: при падении видно, чьё намерение собрано неверно");
    }

    @Test
    void intent_NullId_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new ActivityIntent(null, Optional.of("wait"), Map.of(), Optional.empty()),
                "Намерение без деятельности не исполнить: план духа строится по id деятельности");
    }

    @Test
    void intent_NullContainers_Throw() {
        assertThrows(IllegalArgumentException.class,
                () -> new ActivityIntent(ActivityId.REST, null, Map.of(), Optional.empty()),
                "Намерение без script-контейнера не исполнить: отсутствие скрипта задают через Optional.empty()");
        assertThrows(IllegalArgumentException.class,
                () -> new ActivityIntent(ActivityId.REST, Optional.of("wait"), null, Optional.empty()),
                "Намерение без карты параметров не исполнить: пустой набор задают пустой картой");
        assertThrows(IllegalArgumentException.class,
                () -> new ActivityIntent(ActivityId.REST, Optional.of("wait"), Map.of(), null),
                "Намерение без workKind-контейнера не исполнить: отсутствие брони задают через Optional.empty()");
    }

    @Test
    void demand_NegativeValue_Throws() {
        FixedActivity activity = new FixedActivity(ActivityId.FARM, -1);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> activity.demand(SettlementViews.empty()),
                "Отрицательный спрос ломает сравнение деятельностей: распределение выберет не ту работу");

        assertTrue(error.getMessage().contains("FARM"),
                "Сообщение называет деятельность: при падении видно, чей спрос вышел из диапазона");
    }

    @Test
    void demand_NanValue_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new FixedActivity(ActivityId.MINE, Double.NaN).demand(SettlementViews.empty()),
                "Спрос NaN ломает сортировку спроса: распределение выберет не ту работу");
    }

    @Test
    void demand_AboveMax_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new FixedActivity(ActivityId.CHOP, IntellectTuning.MAX_DEMAND + 1)
                        .demand(SettlementViews.empty()),
                "Спрос выше максимума ломает шкалу: одна деятельность навсегда забьёт остальные");
    }

    @Test
    void demand_NullView_Throws() {
        assertThrows(IllegalArgumentException.class, () -> new FixedActivity(ActivityId.REST, 1).demand(null),
                "Спрос без обстановки не считается: коэффициент зависит от запасов и угроз поселения");
    }

}
