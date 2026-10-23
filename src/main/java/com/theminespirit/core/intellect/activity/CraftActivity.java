package com.theminespirit.core.intellect.activity;

import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.intellect.Activity;
import com.theminespirit.core.intellect.ActivityId;
import com.theminespirit.core.intellect.ActivityIntent;
import com.theminespirit.core.intellect.SettlementView;
import com.theminespirit.core.intellect.WorkRoles;
import com.theminespirit.core.processing.ProcessingRules;
import com.theminespirit.core.processing.ProcessingStock;
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
        ProcessingRules rules = ProcessingRules.loadDefault();
        ProcessingStock stock = view.processing();
        double deficit = Math.max(plankDeficit(stock, rules), stickDeficit(stock, rules));
        if (deficit <= 0) {
            return Activity.checkedDemand(id(), 0);
        }
        return Activity.checkedDemand(id(), rules.baseDemand() + rules.demandScale() * deficit);
    }

    @Override
    public boolean eligible(SpiritSnapshot spirit, SettlementView view) {
        if (spirit == null || view == null) {
            throw new IllegalArgumentException("Крафт без духа или обстановки не проверяется");
        }
        return WorkRoles.canWork(spirit, view);
    }

    @Override
    public ActivityIntent order(SpiritSnapshot spirit, SettlementView view) {
        if (spirit == null || view == null) {
            throw new IllegalArgumentException("Намерение крафта без духа или обстановки не строится");
        }
        ProcessingRules rules = ProcessingRules.loadDefault();
        ProcessingStock stock = view.processing();
        if (plankDeficit(stock, rules) > 0 && stock.plankProduct().isPresent()) {
            return craft(shortName(stock.plankProduct().get()), rules);
        }
        if (stickDeficit(stock, rules) > 0) {
            return craft("stick", rules);
        }
        return new ActivityIntent(id(), Optional.empty(), Map.of(), Optional.empty());
    }

    private static String shortName(String itemId) {
        return itemId.startsWith("minecraft:") ? itemId.substring("minecraft:".length()) : itemId;
    }

    // Запас считается по поселению, а craft берёт только свой инвентарь: дух без брёвен получал невыполнимый крафт.
    // acquire сам берёт из сундука, крафтит из своего или рубит дерево.
    private static ActivityIntent craft(String product, ProcessingRules rules) {
        return new ActivityIntent(
                ActivityId.CRAFT,
                Optional.of(ActivityIntent.ACQUIRE),
                Map.of("item", product, "count", rules.craftCount()),
                Optional.empty());
    }

    private static double plankDeficit(ProcessingStock stock, ProcessingRules rules) {
        return stock.hasLogs() ? deficit(stock.planks(), rules.plankTarget()) : 0;
    }

    private static double stickDeficit(ProcessingStock stock, ProcessingRules rules) {
        return stock.hasPlanks() ? deficit(stock.sticks(), rules.stickTarget()) : 0;
    }

    private static double deficit(int have, int target) {
        if (target <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(1, (target - have) / (double) target));
    }
}
