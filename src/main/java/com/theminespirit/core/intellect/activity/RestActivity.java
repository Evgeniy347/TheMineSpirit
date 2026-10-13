package com.theminespirit.core.intellect.activity;

import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.intellect.Activity;
import com.theminespirit.core.intellect.ActivityId;
import com.theminespirit.core.intellect.ActivityIntent;
import com.theminespirit.core.intellect.SettlementView;
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
        double rest = view.needsFood(1, 1.5) || view.needsOre(64, 32, 2) ? 1.0 : 4.5;
        if (view.dayFraction() >= 0.5) {
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
        return new ActivityIntent(id(), Optional.of("wait"), Map.of(), Optional.empty());
    }
}
