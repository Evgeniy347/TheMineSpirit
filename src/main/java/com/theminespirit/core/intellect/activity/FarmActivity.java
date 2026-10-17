package com.theminespirit.core.intellect.activity;

import com.theminespirit.core.coordinator.SpiritRole;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.intellect.Activity;
import com.theminespirit.core.intellect.ActivityId;
import com.theminespirit.core.intellect.ActivityIntent;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.intellect.SettlementView;
import java.util.Map;
import java.util.Optional;

public final class FarmActivity implements Activity {
    @Override
    public ActivityId id() {
        return ActivityId.FARM;
    }

    @Override
    public double demand(SettlementView view) {
        if (view == null) {
            throw new IllegalArgumentException("Спрос фермы без обстановки не считается: зависит от еды на душу");
        }
        if (!view.farmWork().hasHarvest()) {
            return Activity.checkedDemand(id(), 0);
        }
        double norm = IntellectTuning.FOOD_DAYS * IntellectTuning.FOOD_RATION;
        double need = Math.max(0, Math.min(1, (norm - view.foodNutritionPerSoul()) / norm));
        return Activity.checkedDemand(id(), 1 + 8 * need);
    }

    @Override
    public boolean eligible(SpiritSnapshot spirit, SettlementView view) {
        if (spirit == null || view == null) {
            throw new IllegalArgumentException("Ферма без духа или обстановки не проверяется");
        }
        return spirit.role() == SpiritRole.WORKER || spirit.role() == SpiritRole.IDLE;
    }

    @Override
    public ActivityIntent order(SpiritSnapshot spirit, SettlementView view) {
        if (spirit == null || view == null) {
            throw new IllegalArgumentException("Намерение фермы без духа или обстановки не строится");
        }
        return new ActivityIntent(id(), Optional.of("harvest_crop"), Map.of(), Optional.of(WorkKind.CELL));
    }
}
