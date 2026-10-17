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

class SmeltActivityTests {
    private static final UUID SPIRIT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final SmeltActivity SMELT = new SmeltActivity();
    private static final ProcessingRules RULES = ProcessingRules.defaults();

    @Test
    void demand_NoLogsAndNoRawFood_Zero() {
        SettlementView empty = SettlementViews.processing(3, 0, ProcessingStock.NONE);

        assertEquals(0.0, SMELT.demand(empty),
                "Без брёвен и сырой еды плавить нечего: иначе дух идёт к печи с пустыми руками и падает");
    }

    @Test
    void demand_CharcoalBelowTarget_AboveBase() {
        ProcessingStock stock = new ProcessingStock(0, 0, 0, 0, Map.of(),
                Map.of("minecraft:oak_log", 5));
        SettlementView view = SettlementViews.processing(3, 0, stock);

        assertEquals(RULES.baseDemand() + RULES.demandScale(), SMELT.demand(view),
                "Пустой запас угля при брёвнах даёт максимальный дефицит: иначе уголь не заготавливают, "
                        + "и печи поселения встанут без топлива");
    }

    @Test
    void demand_CharcoalAboveTarget_Zero() {
        ProcessingStock stock = new ProcessingStock(RULES.charcoalTarget(), 0, 0, 0, Map.of(),
                Map.of("minecraft:oak_log", 5));
        SettlementView view = SettlementViews.processing(3, 0, stock);

        assertEquals(0.0, SMELT.demand(view),
                "Набранный запас угля снимает спрос: иначе дух жжёт брёвна бесконечно при полном складе");
    }

    @Test
    void demand_RawFoodWithoutLogs_CookOnly() {
        ProcessingStock stock = new ProcessingStock(0, 0, 0, 0,
                Map.of("minecraft:beef", 1), Map.of());
        SettlementView view = SettlementViews.processing(3, 0, stock);

        assertEquals(RULES.baseDemand() + RULES.demandScale(), SMELT.demand(view),
                "При сыром мясе и пустой готовой еде готовка востребована: иначе поселение ест сырое");
    }

    @Test
    void demand_MoreCharcoal_NotGreater() {
        ProcessingStock none = new ProcessingStock(0, 0, 0, 0, Map.of(), Map.of("minecraft:oak_log", 5));
        ProcessingStock some = new ProcessingStock(8, 0, 0, 0, Map.of(), Map.of("minecraft:oak_log", 5));

        assertTrue(SMELT.demand(SettlementViews.processing(3, 0, some))
                <= SMELT.demand(SettlementViews.processing(3, 0, none)),
                "Больше запаса угля — не больше спрос: иначе распределение гонит плавку при готовом запасе");
    }

    @Test
    void order_CharcoalDeficitGreater_PicksCharcoal() {
        ProcessingStock stock = new ProcessingStock(0, 0, 0, 4.5, Map.of("minecraft:beef", 1),
                Map.of("minecraft:oak_log", 5));
        SettlementView view = SettlementViews.processing(3, 0, stock);

        ActivityIntent intent = SMELT.order(SettlementViews.spirit(SPIRIT), view);

        assertEquals("charcoal", intent.params().get("item"),
                "При большем дефиците угля плавят уголь, а не еду: иначе топливо кончится раньше еды");
        assertEquals(RULES.charcoalCount(), intent.params().get("count"),
                "Число предметов за запуск — из данных: владелец меняет его без пересборки");
    }

    @Test
    void order_OnlyRawFood_PicksCookableProduct() {
        ProcessingStock stock = new ProcessingStock(RULES.charcoalTarget(), 0, 0, 0,
                Map.of("minecraft:beef", 1), Map.of());
        SettlementView view = SettlementViews.processing(3, 0, stock);

        ActivityIntent intent = SMELT.order(SettlementViews.spirit(SPIRIT), view);

        assertEquals("cooked_beef", intent.params().get("item"),
                "Готовят то сырьё, что реально есть: иначе дух зовёт рецепт из отсутствующей говядины");
    }

    @Test
    void order_NoMaterial_EmptyScript() {
        SettlementView empty = SettlementViews.processing(3, 0, ProcessingStock.NONE);

        assertEquals(true, SMELT.order(SettlementViews.spirit(SPIRIT), empty).script().isEmpty(),
                "Без сырья намерения нет: пустой скрипт означает «делать нечего», а не задачу с провалом");
    }
}
