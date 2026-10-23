package com.theminespirit.core.intellect.activity;

import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.intellect.Activity;
import com.theminespirit.core.intellect.ActivityId;
import com.theminespirit.core.intellect.ActivityIntent;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.intellect.SettlementView;
import java.util.Map;
import java.util.Optional;

public final class DefendActivity implements Activity {
    // Без угроз (ночная стража) караулят 1–2 духа, остальные свободны для заданий доски (INC-36); при угрозе бойцов
    // ограничивает maxFighters распределителя.
    public static final int IDLE_GUARDS = 2;

    @Override
    public int crew(SettlementView view) {
        if (view == null) {
            throw new IllegalArgumentException("Число стражей без обстановки не считается: зависит от угроз");
        }
        return view.threats() > 0 ? Integer.MAX_VALUE : IDLE_GUARDS;
    }

    @Override
    public ActivityId id() {
        return ActivityId.DEFEND;
    }

    @Override
    public double demand(SettlementView view) {
        if (view == null) {
            throw new IllegalArgumentException("Спрос защиты без обстановки не считается: зависит от угроз");
        }
        return Activity.checkedDemand(
                id(), view.threats() > 0 ? IntellectTuning.loadDefault().maxDemand() : 0.5);
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
