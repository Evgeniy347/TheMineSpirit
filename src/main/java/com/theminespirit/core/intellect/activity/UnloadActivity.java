package com.theminespirit.core.intellect.activity;

import com.theminespirit.core.coordinator.SpiritRole;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.intellect.Activity;
import com.theminespirit.core.intellect.ActivityId;
import com.theminespirit.core.intellect.ActivityIntent;
import com.theminespirit.core.intellect.FreeInventoryDemand;
import com.theminespirit.core.intellect.InventoryNormTable;
import com.theminespirit.core.intellect.SettlementView;
import com.theminespirit.core.inventory.InventoryFill;
import java.util.Map;
import java.util.Optional;

public final class UnloadActivity implements Activity {
    public static final String SCRIPT = "unload";

    @Override
    public ActivityId id() {
        return ActivityId.UNLOAD;
    }

    @Override
    public double demand(SettlementView view) {
        if (view == null) {
            throw new IllegalArgumentException("Спрос разгрузки без обстановки не считается: зависит от заполнения");
        }
        InventoryNormTable table = InventoryNormTable.loadDefault();
        double highest = 0;
        for (InventoryFill fill : view.inventoryFills().values()) {
            highest = Math.max(highest, FreeInventoryDemand.demand(fill, table));
        }
        return Activity.checkedDemand(id(), highest);
    }

    @Override
    public boolean eligible(SpiritSnapshot spirit, SettlementView view) {
        if (spirit == null || view == null) {
            throw new IllegalArgumentException("Разгрузка без духа или обстановки не проверяется");
        }
        if (spirit.role() != SpiritRole.WORKER && spirit.role() != SpiritRole.IDLE) {
            return false;
        }
        InventoryNormTable table = InventoryNormTable.loadDefault();
        return view.inventoryFill(spirit.id())
                .map(fill -> FreeInventoryDemand.demand(fill, table) > 0)
                .orElse(false);
    }

    @Override
    public ActivityIntent order(SpiritSnapshot spirit, SettlementView view) {
        if (spirit == null || view == null) {
            throw new IllegalArgumentException("Намерение разгрузки без духа или обстановки не строится");
        }
        return new ActivityIntent(id(), Optional.of(SCRIPT), Map.of(), Optional.empty());
    }
}
