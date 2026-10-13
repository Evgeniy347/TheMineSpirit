package com.theminespirit.core.intellect.activity;

import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.intellect.Activity;
import com.theminespirit.core.intellect.ActivityId;
import com.theminespirit.core.intellect.ActivityIntent;
import com.theminespirit.core.intellect.SettlementView;
import java.util.Map;
import java.util.Optional;

public final class DefendActivity implements Activity {
    @Override
    public ActivityId id() {
        return ActivityId.DEFEND;
    }

    @Override
    public double demand(SettlementView view) {
        if (view == null) {
            throw new IllegalArgumentException("Спрос защиты без обстановки не считается: зависит от угроз");
        }
        return Activity.checkedDemand(id(), view.threats() > 0 ? Activity.MAX_DEMAND : 0.5);
    }

    @Override
    public boolean eligible(SpiritSnapshot spirit, SettlementView view) {
        if (spirit == null || view == null) {
            throw new IllegalArgumentException("Защита без духа или обстановки не проверяется");
        }
        return true;
    }

    @Override
    public ActivityIntent order(SpiritSnapshot spirit, SettlementView view) {
        if (spirit == null || view == null) {
            throw new IllegalArgumentException("Намерение защиты без духа или обстановки не строится");
        }
        return new ActivityIntent(id(), Optional.empty(), Map.of(), Optional.empty());
    }
}
