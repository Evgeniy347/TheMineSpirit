package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.SpiritSnapshot;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Arbitraries;

class ActivityCatalogProperties {
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

    @Provide
    Arbitrary<ActivityCatalog> catalogs() {
        return Arbitraries.subsetOf(EnumSet.allOf(ActivityId.class)).map(ids -> {
            List<Activity> activities = new ArrayList<>();
            for (ActivityId id : ids) {
                activities.add(new FixedActivity(id));
            }
            return ActivityCatalog.adultsOnly(activities);
        });
    }

    @Property
    void ordered_AnyCatalog_SortedByActivityId(@ForAll("catalogs") ActivityCatalog catalog) {
        List<Activity> ordered = catalog.ordered();

        assertEquals(catalog.forChild(false).size(), ordered.size(),
                "Обход отдаёт каждую деятельность каталога ровно раз: иначе часть работ не получит спроса");
        for (int index = 1; index < ordered.size(); index++) {
            assertTrue(ordered.get(index - 1).id().compareTo(ordered.get(index).id()) < 0,
                    "Обход каталога упорядочен по ActivityId: иначе порядок спроса зависит от hash карты");
        }
    }
}
