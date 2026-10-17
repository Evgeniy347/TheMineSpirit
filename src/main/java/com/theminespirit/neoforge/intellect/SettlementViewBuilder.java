package com.theminespirit.neoforge.intellect;

import com.theminespirit.core.coordinator.SpiritSnapshot;
import com.theminespirit.core.intellect.FarmWork;
import com.theminespirit.core.intellect.Forage;
import com.theminespirit.core.intellect.SettlementView;
import com.theminespirit.core.intellect.StockLots;
import com.theminespirit.core.inventory.BaseKit;
import com.theminespirit.core.inventory.InventoryFill;
import com.theminespirit.core.inventory.InventoryNorm;
import com.theminespirit.core.inventory.KitFor;
import com.theminespirit.core.inventory.NeedsOfPlan;
import com.theminespirit.core.inventory.ValueTable;
import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.crafting.RecipeKnowledge;
import com.theminespirit.neoforge.inventory.InventoryTables;
import com.theminespirit.neoforge.inventory.StackMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

public final class SettlementViewBuilder {
    private SettlementViewBuilder() {
    }

    public static SettlementView build(List<SpiritSnapshot> snapshots, List<SpiritEntity> members,
            StockLots lots, Optional<Point> home, List<EnemyFact> enemies, Forage forage, ServerLevel level,
            long tick) {
        if (snapshots == null || members == null || lots == null || home == null || enemies == null
                || forage == null || level == null) {
            throw new IllegalArgumentException(
                    "Снимок поселения без духов, запасов, дома, врагов, добычи или уровня не собрать: "
                            + "вид зависит от всех сразу");
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
        FarmWork farmWork = FarmWorkScanner.read(level, home, members, lots.seeds());
        return new SettlementView(population, children, foodPerSoul, nutritionPerSoul, lots.ore(), lots.iron(),
                lots.weapons(), enemies.size(), dayFraction, farmWork, forage, ordered, tick, lots.processing(),
                inventoryFills(members, level));
    }

    private static Map<UUID, InventoryFill> inventoryFills(List<SpiritEntity> members, ServerLevel level) {
        RecipeKnowledge recipes = RecipeKnowledge.of(level);
        ValueTable values = InventoryTables.values();
        StackMapper mapper = new StackMapper();
        Map<UUID, InventoryFill> fills = new HashMap<>();
        for (SpiritEntity spirit : members) {
            if (spirit.isRemoved()) {
                continue;
            }
            BaseKit kit = KitFor.kitFor(spirit.currentActivity().orElse(null), spirit.isChild());
            List<String> needs = spirit.currentPlan()
                    .map(plan -> NeedsOfPlan.collect(plan, recipes))
                    .orElse(List.of());
            fills.put(spirit.getUUID(),
                    InventoryNorm.fill(mapper.map(spirit.getInventory()), kit, needs, values));
        }
        return Map.copyOf(fills);
    }
}
