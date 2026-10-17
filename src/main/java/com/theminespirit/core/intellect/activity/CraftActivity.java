package com.theminespirit.core.intellect.activity;

import com.theminespirit.core.coordinator.SpiritRole;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.intellect.Activity;
import com.theminespirit.core.intellect.ActivityId;
import com.theminespirit.core.intellect.ActivityIntent;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.intellect.SettlementView;
import java.util.Map;
import java.util.Optional;

public final class CraftActivity implements Activity {
    @Override
    public ActivityId id() {
        return ActivityId.CRAFT;
    }

    @Override
    public double demand(SettlementView view) {
        if (view == null) {
            throw new IllegalArgumentException("Спрос крафта без обстановки не считается: зависит от запасов");
        }
        return Activity.checkedDemand(id(), 2.0);
    }

    @Override
    public boolean eligible(SpiritSnapshot spirit, SettlementView view) {
        if (spirit == null || view == null) {
            throw new IllegalArgumentException("Крафт без духа или обстановки не проверяется");
        }
        return spirit.role() == SpiritRole.WORKER || spirit.role() == SpiritRole.IDLE;
    }

    @Override
    public ActivityIntent order(SpiritSnapshot spirit, SettlementView view) {
        if (spirit == null || view == null) {
            throw new IllegalArgumentException("Намерение крафта без духа или обстановки не строится");
        }
        String product = IntellectTuning.PLANK_TARGET > 0 ? "oak_planks" : "stick";
        return new ActivityIntent(id(), Optional.of("craft"),
                Map.of("item", product, "count", 8, "radius", 16), Optional.empty());
    }
}