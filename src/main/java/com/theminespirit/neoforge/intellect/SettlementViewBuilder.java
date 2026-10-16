package com.theminespirit.neoforge.intellect;

import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.intellect.SettlementView;
import com.theminespirit.core.intellect.StockLots;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;

public final class SettlementViewBuilder {
    private SettlementViewBuilder() {
    }

    public static SettlementView build(List<SpiritSnapshot> snapshots, List<SpiritEntity> members,
            StockLots lots, List<EnemyFact> enemies, ServerLevel level, long tick) {
        if (snapshots == null || members == null || lots == null || enemies == null || level == null) {
            throw new IllegalArgumentException(
                    "Снимок поселения без духов, запасов, врагов или уровня не собрать: вид зависит от всех сразу");
        }
        if (tick < 0) {
            throw new IllegalArgumentException("Тик снимка " + tick + ": время не может быть отрицательным");
        }
        List<SpiritSnapshot> ordered = new ArrayList<>(snapshots);
        ordered.sort(Comparator.comparing(SpiritSnapshot::id));
        int population = ordered.size();
        int children = 0;
        for (SpiritEntity spirit : members) {
            if (!spirit.isRemoved() && spirit.isChild()) {
                children++;
            }
        }
        children = Math.min(children, population);
        double foodPerSoul = population == 0 ? 0 : (double) lots.foodItems() / population;
        double nutritionPerSoul = population == 0 ? 0 : lots.foodNutrition() / population;
        double dayFraction = (level.getDayTime() % 24000L) / 24000.0;
        return new SettlementView(population, children, foodPerSoul, nutritionPerSoul, lots.ore(), lots.iron(),
                lots.weapons(), enemies.size(), dayFraction, ordered, tick);
    }
}
