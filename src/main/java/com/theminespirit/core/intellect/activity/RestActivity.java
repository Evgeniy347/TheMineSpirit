package com.theminespirit.core.intellect.activity;

import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.intellect.Activity;
import com.theminespirit.core.intellect.ActivityId;
import com.theminespirit.core.intellect.ActivityIntent;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.intellect.SettlementView;
import com.theminespirit.core.intellect.WorkStations;
import com.theminespirit.core.sleep.SleepRule;
import java.util.Map;
import java.util.Optional;

public final class RestActivity implements Activity {
    @Override
    public ActivityId id() {
        return ActivityId.REST;
    }

    @Override
    public double demand(SettlementView view) {
        if (view == null) {
            throw new IllegalArgumentException("Спрос отдыха без обстановки не считается: зависит от нужд и ночи");
        }
        IntellectTuning tuning = IntellectTuning.loadDefault();
        double rest = view.foodDeficit(tuning.foodDays(), tuning.foodRation(), tuning.foodChildRation())
                                        .deficit()
                                > 0
                        || view.needsOre(tuning.oreTarget(), tuning.ironTarget(), tuning.weaponTarget())
                ? 1.0
                : 4.5;
        if (SleepRule.isNight(view.dayFraction())) {
            rest = Math.max(rest, 6.0);
        }
        return Activity.checkedDemand(id(), rest);
    }

    @Override
    public boolean eligible(SpiritSnapshot spirit, SettlementView view) {
        if (spirit == null || view == null) {
            throw new IllegalArgumentException("Отдых без духа или обстановки не проверяется");
        }
        return true;
    }

    @Override
    public int crew(SettlementView view) {
        if (view == null) {
            throw new IllegalArgumentException("Отдых без обстановки не делит бригаду: нужен снимок поселения");
        }
        if (!SleepRule.isNight(view.dayFraction())) {
            return Integer.MAX_VALUE;
        }
        return view.stations().freeBeds();
    }

    @Override
    public ActivityIntent order(SpiritSnapshot spirit, SettlementView view) {
        if (spirit == null || view == null) {
            throw new IllegalArgumentException("Намерение отдыха без духа или обстановки не строится");
        }
        if (!SleepRule.isNight(view.dayFraction())) {
            return new ActivityIntent(id(), Optional.of("wait"), Map.of(), Optional.empty());
        }
        WorkStations stations = view.stations();
        if (!stations.canSleep()) {
            return new ActivityIntent(id(), Optional.empty(), Map.of(), Optional.empty());
        }
        return new ActivityIntent(id(), Optional.of("sleep"), Map.of(), Optional.empty());
    }
}
