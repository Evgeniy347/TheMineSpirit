package com.theminespirit.core.intellect.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.intellect.ActivityIntent;
import com.theminespirit.core.intellect.SettlementView;
import com.theminespirit.core.intellect.SettlementViews;
import com.theminespirit.core.processing.ProcessingRules;
import com.theminespirit.core.processing.ProcessingStock;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CraftActivityTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final CraftActivity CRAFT = new CraftActivity();
    private static final ProcessingRules RULES = ProcessingRules.defaults();

    @Test
    void demand_NoLogsAndNoPlanks_Zero() {
        SettlementView empty = SettlementViews.processing(3, 0, ProcessingStock.NONE);

        assertEquals(0.0, CRAFT.demand(empty),
                "Без брёвен и досок крафтить нечего: иначе спрос зовёт бесконечный крафт досок на пустом складе");
    }

    @Test
    void demand_PlanksBelowTarget_AboveBase() {
        SettlementView view = SettlementViews.processing(3, 0,
                new ProcessingStock(0, 0, 0, 0, Map.of(), Map.of("minecraft:oak_log", 5)));

        assertEquals(RULES.baseDemand() + RULES.demandScale(), CRAFT.demand(view),
                "Пустой запас досок при брёвнах даёт максимальный дефицит: иначе доски не заготавливают");
    }

    @Test
    void demand_PlanksEnoughSticksLow_FromStickDeficit() {
        ProcessingStock stock = new ProcessingStock(0, RULES.plankTarget(), 0, 0, Map.of(),
                Map.of("minecraft:oak_log", 5));

        assertEquals(RULES.baseDemand() + RULES.demandScale(), CRAFT.demand(SettlementViews.processing(3, 0, stock)),
                "Полные доски, но пустые палки дают спрос на палки: иначе инструменты стоят без палок");
    }

    @Test
    void demand_BothStocked_Zero() {
        ProcessingStock stock = new ProcessingStock(0, RULES.plankTarget(), RULES.stickTarget(), 0, Map.of(),
                Map.of("minecraft:oak_log", 5));

        assertEquals(0.0, CRAFT.demand(SettlementViews.processing(3, 0, stock)),
                "Полные доски и палки снимают спрос: иначе бесконечный крафт досок при полном складе");
    }

    @Test
    void order_PlanksDeficit_CraftsPlanksOfAvailableLog() {
        SettlementView view = SettlementViews.processing(3, 0,
                new ProcessingStock(0, 0, 0, 0, Map.of(), Map.of("minecraft:birch_log", 5)));

        ActivityIntent intent = CRAFT.order(SettlementViews.spirit(SPIRIT), view);

        assertEquals("birch_planks", intent.params().get("item"),
                "Доски делают из имеющейся породы: иначе рецепт из чужого дерева не найдётся");
    }

    @Test
    void order_PlanksEnoughSticksLow_CraftsSticks() {
        ProcessingStock stock = new ProcessingStock(0, RULES.plankTarget(), 0, 0, Map.of(),
                Map.of("minecraft:oak_log", 5));
        SettlementView view = SettlementViews.processing(3, 0, stock);

        assertEquals("stick", CRAFT.order(SettlementViews.spirit(SPIRIT), view).params().get("item"),
                "Палки делают, когда доски есть, а палок мало: иначе дух жжёт доски в доски");
    }

    @Test
    void order_NoMaterial_EmptyScript() {
        SettlementView empty = SettlementViews.processing(3, 0, ProcessingStock.NONE);

        assertTrue(CRAFT.order(SettlementViews.spirit(SPIRIT), empty).script().isEmpty(),
                "Без сырья намерения нет: пустой скрипт означает «делать нечего», а не задачу с провалом");
    }
}
