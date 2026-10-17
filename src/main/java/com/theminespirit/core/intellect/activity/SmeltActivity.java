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

public final class SmeltActivity implements Activity {
    @Override
    public ActivityId id() {
        return ActivityId.SMELT;
    }

    @Override
    public double demand(SettlementView view) {
        if (view == null) {
            throw new IllegalArgumentException("Спрос плавки без обстановки не считается: зависит от запасов");
        }
        double charcoalDeficit = deficit(view.ore(), IntellectTuning.CHARCOAL_TARGET);
        double foodNorm = IntellectTuning.FOOD_DAYS * IntellectTuning.FOOD_SAFETY;
        double foodDeficit = Math.max(0, Math.min(1, (foodNorm - view.foodNutritionPerSoul()) / foodNorm));
        double deficit = Math.max(charcoalDeficit, foodDeficit);
        return Activity.checkedDemand(id(), 1 + 8 * deficit);
    }

    @Override
    public boolean eligible(SpiritSnapshot spirit, SettlementView view) {
        if (spirit == null || view == null) {
            throw new IllegalArgumentException("Плавка без духа или обстановки не проверяется");
        }
        return spirit.role() == SpiritRole.WORKER || spirit.role() == SpiritRole.IDLE;
    }

    @Override
    public ActivityIntent order(SpiritSnapshot spirit, SettlementView view) {
        if (spirit == null || view == null) {
            throw new IllegalArgumentException("Намерение плавки без духа или обстановки не строится");
        }
        String product = view.ore() < IntellectTuning.CHARCOAL_TARGET ? "charcoal" : "cooked_beef";
        int count = product.equals("charcoal") ? 8 : 4;
        return new ActivityIntent(id(), Optional.of("smelt"),
                Map.of("item", product, "count", count, "radius", 16), Optional.empty());
    }

    private static double deficit(int have, int target) {
        if (target <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(1, (target - have) / (double) target));
    }
}