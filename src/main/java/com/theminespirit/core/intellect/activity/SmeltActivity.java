package com.theminespirit.core.intellect.activity;

import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.intellect.Activity;
import com.theminespirit.core.intellect.ActivityId;
import com.theminespirit.core.intellect.ActivityIntent;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.intellect.SettlementView;
import com.theminespirit.core.intellect.StockCatalog;
import com.theminespirit.core.intellect.WorkRoles;
import com.theminespirit.core.intellect.WorkStations;
import com.theminespirit.core.processing.CookableFoods;
import com.theminespirit.core.processing.ProcessingRules;
import com.theminespirit.core.processing.ProcessingStock;
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
        WorkStations stations = view.stations();
        if (!stations.canSmelt()) {
            return Activity.checkedDemand(id(), 0);
        }
        ProcessingRules rules = ProcessingRules.loadDefault();
        ProcessingStock stock = view.processing();
        double deficit = Math.max(charcoalDeficit(stock, rules), cookDeficit(view, stock));
        if (deficit <= 0) {
            return Activity.checkedDemand(id(), 0);
        }
        return Activity.checkedDemand(id(), rules.baseDemand() + rules.demandScale() * deficit);
    }

    @Override
    public boolean eligible(SpiritSnapshot spirit, SettlementView view) {
        if (spirit == null || view == null) {
            throw new IllegalArgumentException("Плавка без духа или обстановки не проверяется");
        }
        return WorkRoles.canWork(spirit, view);
    }

    @Override
    public int crew(SettlementView view) {
        if (view == null) {
            throw new IllegalArgumentException("Плавка без обстановки не делит бригаду: нужен снимок поселения");
        }
        WorkStations stations = view.stations();
        if (!stations.canSmelt()) {
            return 0;
        }
        return Math.max(1, stations.smeltStations());
    }

    @Override
    public ActivityIntent order(SpiritSnapshot spirit, SettlementView view) {
        if (spirit == null || view == null) {
            throw new IllegalArgumentException("Намерение плавки без духа или обстановки не строится");
        }
        WorkStations stations = view.stations();
        if (!stations.canSmelt()) {
            return new ActivityIntent(id(), Optional.empty(), Map.of(), Optional.empty());
        }
        ProcessingRules rules = ProcessingRules.loadDefault();
        ProcessingStock stock = view.processing();
        double charcoalDeficit = charcoalDeficit(stock, rules);
        double cookDeficit = cookDeficit(view, stock);
        if (charcoalDeficit <= 0 && cookDeficit <= 0) {
            return new ActivityIntent(id(), Optional.empty(), Map.of(), Optional.empty());
        }
        if (stations.mustCraftFurnace()) {
            return new ActivityIntent(
                    id(),
                    Optional.of("craft"),
                    Map.of("item", "furnace", "count", 1, "radius", rules.stationRadius()),
                    Optional.empty());
        }
        if (charcoalDeficit >= cookDeficit) {
            // Уголь — через acquire: брёвна и топливо дух берёт из сундука или рубит сам, а не падает «не хватает»
            // без своих брёвен; count — сколько добавить к своему (как у крафта досок).
            return new ActivityIntent(
                    id(),
                    Optional.of(ActivityIntent.ACQUIRE),
                    Map.of("item", "charcoal", "count", rules.charcoalCount()),
                    Optional.empty());
        }
        Optional<String> cooked = CookableFoods.best(stock.rawFoods(), StockCatalog.defaultTable());
        if (cooked.isEmpty()) {
            return new ActivityIntent(id(), Optional.empty(), Map.of(), Optional.empty());
        }
        return smelt(shortName(cooked.get()), rules.cookCount(), rules);
    }

    private static String shortName(String itemId) {
        return itemId.startsWith("minecraft:") ? itemId.substring("minecraft:".length()) : itemId;
    }

    private static ActivityIntent smelt(String product, int count, ProcessingRules rules) {
        return new ActivityIntent(
                ActivityId.SMELT,
                Optional.of("smelt"),
                Map.of("item", product, "count", count, "radius", rules.stationRadius()),
                Optional.empty());
    }

    private static double charcoalDeficit(ProcessingStock stock, ProcessingRules rules) {
        return stock.hasLogs() ? deficit(stock.charcoal(), rules.charcoalTarget()) : 0;
    }

    private static double cookDeficit(SettlementView view, ProcessingStock stock) {
        if (!stock.hasRawFood()) {
            return 0;
        }
        IntellectTuning tuning = IntellectTuning.loadDefault();
        double norm = tuning.foodDays() * tuning.foodRation();
        double perSoul = view.population() == 0 ? 0 : stock.cookedNutrition() / view.population();
        return Math.max(0, Math.min(1, (norm - perSoul) / norm));
    }

    private static double deficit(int have, int target) {
        if (target <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(1, (target - have) / (double) target));
    }
}
