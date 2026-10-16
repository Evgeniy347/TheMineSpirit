package com.theminespirit.core.intellect.activity;

import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.intellect.Activity;
import com.theminespirit.core.intellect.ActivityId;
import com.theminespirit.core.intellect.ActivityIntent;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.intellect.SettlementView;
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
        double rest = view.needsFood(IntellectTuning.FOOD_DAYS, IntellectTuning.FOOD_SAFETY)
                || view.needsOre(IntellectTuning.ORE_TARGET, IntellectTuning.IRON_TARGET,
                        IntellectTuning.WEAPON_TARGET) ? 1.0 : 4.5;
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
    public ActivityIntent order(SpiritSnapshot spirit, SettlementView view) {
        if (spirit == null || view == null) {
            throw new IllegalArgumentException("Намерение отдыха без духа или обстановки не строится");
        }
        String script = SleepRule.isNight(view.dayFraction()) ? "sleep" : "wait";
        return new ActivityIntent(id(), Optional.of(script), Map.of(), Optional.empty());
    }
}
