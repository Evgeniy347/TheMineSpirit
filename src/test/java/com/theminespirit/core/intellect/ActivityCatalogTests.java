package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.SpiritSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ActivityCatalogTests {
    private static final class FixedActivity implements Activity {
        private final ActivityId activityId;

        FixedActivity(ActivityId activityId) {
            this.activityId = activityId;
        }

        @Override
        public ActivityId id() {
            return activityId;
        }

        @Override
        public double demand(SettlementView view) {
            return Activity.checkedDemand(activityId, 1);
        }

        @Override
        public boolean eligible(SpiritSnapshot spirit, SettlementView view) {
            return true;
        }

        @Override
        public ActivityIntent order(SpiritSnapshot spirit, SettlementView view) {
            return new ActivityIntent(activityId, Optional.of("wait"), Map.of(), Optional.empty());
        }
    }

    @Test
    void forChild_AdultAndChildCatalogs_DoNotMix() {
        ActivityCatalog catalog = new ActivityCatalog(Map.of(ActivityId.CHOP, new FixedActivity(ActivityId.CHOP)),
                Map.of(ActivityId.REST, new FixedActivity(ActivityId.REST)));

        assertEquals(List.of(ActivityId.CHOP), List.copyOf(catalog.forChild(false).keySet()),
                "Взрослый каталог не содержит детских id: иначе взрослый дух возьмёт детскую деятельность");
        assertEquals(List.of(ActivityId.REST), List.copyOf(catalog.forChild(true).keySet()),
                "Детский каталог не содержит взрослых id: иначе ребёнок возьмёт взрослую деятельность");
    }

    @Test
    void forChild_FirstStageChildCatalog_IsEmpty() {
        ActivityCatalog catalog = ActivityCatalog.adultsOnly(
                List.of(new FixedActivity(ActivityId.CHOP), new FixedActivity(ActivityId.FARM)));

        assertTrue(catalog.forChild(true).isEmpty(),
                "Детский каталог первого этапа пуст: дети появятся с чеклистом Социальность, сейчас children = 0");
    }

    @Test
    void byId_UnknownId_Throws() {
        ActivityCatalog catalog = ActivityCatalog.adultsOnly(List.of(new FixedActivity(ActivityId.CHOP)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> catalog.byId(ActivityId.MINE),
                "Неизвестная деятельность не ищется: дух не возьмёт работу, которой нет в каталоге");

        assertTrue(error.getMessage().contains("MINE"),
                "Сообщение называет id: при падении видно, какой деятельности нет в каталоге");
    }

    @Test
    void byId_KnownId_ReturnsSameActivity() {
        FixedActivity chop = new FixedActivity(ActivityId.CHOP);
        ActivityCatalog catalog = ActivityCatalog.adultsOnly(List.of(chop));

        assertSame(chop, catalog.byId(ActivityId.CHOP),
                "Каталог отдаёт ту же деятельность: спрос и намерение берутся из одного класса, а не копии");
    }

    @Test
    void adultsOnly_DuplicateId_Throws() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ActivityCatalog.adultsOnly(
                        List.of(new FixedActivity(ActivityId.CHOP), new FixedActivity(ActivityId.CHOP))),
                "Дубль id в каталоге не собирается: распределитель не решит, чьим спросом пользоваться");

        assertTrue(error.getMessage().contains("CHOP"),
                "Сообщение называет дубль: при падении видно, какая деятельность задвоилась");
    }

    @Test
    void constructor_NullMapsAndNullEntries_Throw() {
        assertThrows(IllegalArgumentException.class, () -> new ActivityCatalog(null, Map.of()),
                "Каталог с null взрослых не собирается: пустой каталог задают пустой картой");
        assertThrows(IllegalArgumentException.class, () -> new ActivityCatalog(Map.of(), null),
                "Каталог с null детей не собирается: пустой каталог задают пустой картой");
    }
}
