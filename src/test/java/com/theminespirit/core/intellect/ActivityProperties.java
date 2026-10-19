package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.coordinator.SpiritSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class ActivityProperties {
    private static final Set<String> KNOWN_SCRIPTS =
            Set.of("chop_tree", "harvest_crop", "mine_vein", "wait", "return_home");

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
            return Activity.checkedDemand(
                    activityId, (List.of(ActivityId.values()).indexOf(activityId) + 1) * 1.0);
        }

        @Override
        public boolean eligible(SpiritSnapshot spirit, SettlementView view) {
            return spirit.hpFraction() > 0;
        }

        @Override
        public ActivityIntent order(SpiritSnapshot spirit, SettlementView view) {
            return new ActivityIntent(activityId, Optional.of("wait"), Map.of(), Optional.empty());
        }
    }

    @Provide
    Arbitrary<SettlementView> views() {
        return SettlementViews.views();
    }

    @Provide
    Arbitrary<List<SpiritSnapshot>> spirits() {
        return SettlementViews.views().map(SettlementView::spirits);
    }

    @Property
    void demand_AnyView_DeterministicAndBounded(@ForAll("views") SettlementView view) {
        for (ActivityId id : ActivityId.values()) {
            Activity activity = new FixedActivity(id);

            assertEquals(
                    activity.demand(view),
                    activity.demand(view),
                    "Спрос " + id + " детерминирован: иначе распределение мигает между пересборками");
            assertTrue(
                    activity.demand(view) >= 0
                            && activity.demand(view)
                                    <= IntellectTuning.loadDefault().maxDemand(),
                    "Спрос " + id + " лежит в 0..MAX_DEMAND: иначе сравнение деятельностей несравнимо");
        }
    }

    @Property
    void order_EligibleSpirit_ScriptFromKnownSetOrEmpty(@ForAll("views") SettlementView view) {
        for (ActivityId id : ActivityId.values()) {
            Activity activity = new FixedActivity(id);
            for (SpiritSnapshot spirit : view.spirits()) {
                if (!activity.eligible(spirit, view)) {
                    continue;
                }
                Optional<String> script = activity.order(spirit, view).script();

                assertTrue(
                        script.isEmpty() || KNOWN_SCRIPTS.contains(script.get()),
                        "Намерение " + id + " ссылается на известный скрипт или пусто: "
                                + "неизвестный скрипт дух не исполнит");
            }
        }
    }

    @Property
    void eligible_RepeatedCalls_Deterministic(@ForAll("views") SettlementView view) {
        for (ActivityId id : ActivityId.values()) {
            Activity activity = new FixedActivity(id);
            for (SpiritSnapshot spirit : view.spirits()) {
                assertEquals(
                        activity.eligible(spirit, view),
                        activity.eligible(spirit, view),
                        "Пригодность " + id + " не зависит от порядка вызовов: иначе жребий вместо отбора");
            }
        }
    }
}
