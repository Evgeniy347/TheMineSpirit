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

public final class MineActivity implements Activity {
    @Override
    public ActivityId id() {
        return ActivityId.MINE;
    }

    @Override
    public double demand(SettlementView view) {
        if (view == null) {
            throw new IllegalArgumentException("Спрос добычи без обстановки не считается: зависит от запасов");
        }
        IntellectTuning tuning = IntellectTuning.loadDefault();
        double deficit = Math.max(deficit(view.ore(), tuning.oreTarget()), Math.max(
                deficit(view.iron(), tuning.ironTarget()),
                deficit(view.weapons(), tuning.weaponTarget())));
        return Activity.checkedDemand(id(), 1 + 8 * deficit);
    }

    @Override
    public boolean eligible(SpiritSnapshot spirit, SettlementView view) {
        if (spirit == null || view == null) {
            throw new IllegalArgumentException("Добыча без духа или обстановки не проверяется");
        }
        return spirit.role() == SpiritRole.WORKER;
    }

    @Override
    public boolean external() {
        return true;
    }

    @Override
    public ActivityIntent order(SpiritSnapshot spirit, SettlementView view) {
        if (spirit == null || view == null) {
            throw new IllegalArgumentException("Намерение добычи без духа или обстановки не строится");
        }
        return new ActivityIntent(id(), Optional.of("mine_vein"), Map.of(), Optional.of(WorkKind.VEIN));
    }

    private static double deficit(int have, int target) {
        return Math.max(0, Math.min(1, (target - have) / (double) target));
    }
}
