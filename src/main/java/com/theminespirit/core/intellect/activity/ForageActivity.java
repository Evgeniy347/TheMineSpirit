package com.theminespirit.core.intellect.activity;

import com.theminespirit.core.coordinator.SpiritRole;
import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.intellect.Activity;
import com.theminespirit.core.intellect.ActivityId;
import com.theminespirit.core.intellect.ActivityIntent;
import com.theminespirit.core.intellect.Forage;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.intellect.SettlementView;
import java.util.Map;
import java.util.Optional;

public final class ForageActivity implements Activity {
    @Override
    public ActivityId id() {
        return ActivityId.FORAGE;
    }

    @Override
    public double demand(SettlementView view) {
        if (view == null) {
            throw new IllegalArgumentException("Спрос добычи еды без обстановки не считается: зависит от еды на душу");
        }
        if (!hasMethod(view)) {
            return Activity.checkedDemand(id(), 0);
        }
        double norm = IntellectTuning.FOOD_DAYS * IntellectTuning.FOOD_RATION;
        double need = Math.max(0, Math.min(1, (norm - view.foodNutritionPerSoul()) / norm));
        return Activity.checkedDemand(id(), 1 + 8 * need);
    }

    @Override
    public boolean eligible(SpiritSnapshot spirit, SettlementView view) {
        if (spirit == null || view == null) {
            throw new IllegalArgumentException("Добыча еды без духа или обстановки не проверяется");
        }
        return spirit.role() == SpiritRole.WORKER || spirit.role() == SpiritRole.IDLE;
    }

    @Override
    public ActivityIntent order(SpiritSnapshot spirit, SettlementView view) {
        if (spirit == null || view == null) {
            throw new IllegalArgumentException("Намерение добычи еды без духа или обстановки не строится");
        }
        Forage forage = view.forage();
        if (forage.hasContainerFood()) {
            return new ActivityIntent(id(), Optional.of("withdraw"),
                    Map.of("container", "nearest", "item", forage.containerFood(),
                            "count", IntellectTuning.FORAGE_WITHDRAW_COUNT,
                            "radius", IntellectTuning.FORAGE_WITHDRAW_RADIUS),
                    Optional.empty());
        }
        if (view.farmWork().hasHarvest()) {
            return new ActivityIntent(id(), Optional.of("harvest_crop"), Map.of(), Optional.of(WorkKind.CELL));
        }
        if (forage.canFish()) {
            return new ActivityIntent(id(), Optional.of("fish"),
                    Map.of("count", IntellectTuning.FORAGE_FISH_COUNT,
                            "radius", IntellectTuning.FORAGE_FISH_RADIUS,
                            "duration_ticks", IntellectTuning.FORAGE_FISH_DURATION),
                    Optional.empty());
        }
        if (forage.hasPrey() && view.weapons() > 0) {
            return new ActivityIntent(id(), Optional.of("hunt"),
                    Map.of("species", forage.preySpecies(), "radius", IntellectTuning.FORAGE_HUNT_RADIUS,
                            "count", IntellectTuning.FORAGE_HUNT_COUNT),
                    Optional.empty());
        }
        return new ActivityIntent(id(), Optional.empty(), Map.of(), Optional.empty());
    }

    private static boolean hasMethod(SettlementView view) {
        return view.forage().hasContainerFood() || view.farmWork().hasHarvest()
                || view.forage().canFish()
                || (view.forage().hasPrey() && view.weapons() > 0);
    }
}
