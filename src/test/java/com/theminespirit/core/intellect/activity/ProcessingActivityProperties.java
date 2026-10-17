package com.theminespirit.core.intellect.activity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.intellect.SettlementView;
import com.theminespirit.core.intellect.SettlementViews;
import com.theminespirit.core.processing.ProcessingStock;
import java.util.Map;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class ProcessingActivityProperties {
    private static final SmeltActivity SMELT = new SmeltActivity();
    private static final CraftActivity CRAFT = new CraftActivity();

    @Property
    void demand_MoreCharcoal_NotGreater(@ForAll @IntRange(min = 0, max = 40) int have) {
        ProcessingStock less = new ProcessingStock(have, 0, 0, 0, Map.of(), Map.of("minecraft:oak_log", 5));
        ProcessingStock more = new ProcessingStock(have + 1, 0, 0, 0, Map.of(), Map.of("minecraft:oak_log", 5));

        assertTrue(SMELT.demand(SettlementViews.processing(3, 0, more))
                        <= SMELT.demand(SettlementViews.processing(3, 0, less)),
                "Больше запаса угля — не больше спрос плавки: добыча не притворяется нужной при полном складе");
    }

    @Property
    void demand_MorePlanks_NotGreater(@ForAll @IntRange(min = 0, max = 40) int have) {
        ProcessingStock less = new ProcessingStock(0, have, 0, 0, Map.of(), Map.of("minecraft:oak_log", 5));
        ProcessingStock more = new ProcessingStock(0, have + 1, 0, 0, Map.of(), Map.of("minecraft:oak_log", 5));

        assertTrue(CRAFT.demand(SettlementViews.processing(3, 0, more))
                        <= CRAFT.demand(SettlementViews.processing(3, 0, less)),
                "Больше досок — не больше спрос крафта: иначе бесконечный крафт досок при полном складе");
    }

    @Property
    void demand_AnyStock_WithinBounds(@ForAll @IntRange(min = 0, max = 100) int size) {
        ProcessingStock stock = new ProcessingStock(size, size, size, size,
                Map.of("minecraft:beef", size), Map.of("minecraft:oak_log", size));
        SettlementView view = SettlementViews.processing(3, 0, stock);

        double maxDemand = IntellectTuning.loadDefault().maxDemand();
        assertTrue(SMELT.demand(view) >= 0 && SMELT.demand(view) <= maxDemand,
                "Спрос плавки лежит в 0..MAX_DEMAND: иначе сравнение деятельностей несравнимо");
        assertTrue(CRAFT.demand(view) >= 0 && CRAFT.demand(view) <= maxDemand,
                "Спрос крафта лежит в 0..MAX_DEMAND: иначе сравнение деятельностей несравнимо");
    }
}
