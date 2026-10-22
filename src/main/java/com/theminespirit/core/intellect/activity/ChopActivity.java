package com.theminespirit.core.intellect.activity;

import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.intellect.Activity;
import com.theminespirit.core.intellect.ActivityId;
import com.theminespirit.core.intellect.ActivityIntent;
import com.theminespirit.core.intellect.SettlementView;
import com.theminespirit.core.intellect.WorkRoles;
import java.util.Map;
import java.util.Optional;

public final class ChopActivity implements Activity {
    @Override
    public ActivityId id() {
        return ActivityId.CHOP;
    }

    @Override
    public double demand(SettlementView view) {
        requireView(view);
        return Activity.checkedDemand(id(), 3.0);
    }

    @Override
    public boolean eligible(SpiritSnapshot spirit, SettlementView view) {
        requireSpirit(spirit, view);
        return WorkRoles.canWork(spirit, view);
    }

    @Override
    public ActivityIntent order(SpiritSnapshot spirit, SettlementView view) {
        requireSpirit(spirit, view);
        return new ActivityIntent(id(), Optional.of("chop_tree"), Map.of(), Optional.of(WorkKind.TREE));
    }

    private void requireView(SettlementView view) {
        if (view == null) {
            throw new IllegalArgumentException("Спрос рубки без обстановки не считается: зависит от нужд поселения");
        }
    }

    private void requireSpirit(SpiritSnapshot spirit, SettlementView view) {
        if (spirit == null || view == null) {
            throw new IllegalArgumentException("Рубка без духа или обстановки не проверяется");
        }
    }
}
