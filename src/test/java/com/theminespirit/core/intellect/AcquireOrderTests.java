package com.theminespirit.core.intellect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.script.CatalogLimits;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AcquireOrderTests {
    @Test
    void of_SpiritHoldsPlanks_TargetAddsToOwn() {
        AcquireOrder order = AcquireOrder.of("oak_planks", 8, Map.of("minecraft:oak_planks", 8));

        assertEquals(
                16,
                order.count(),
                "acquire считает цель по своему инвентарю: без поправки дух с 8 досками «успевает» сразу и запас "
                        + "поселения не растёт");
    }

    @Test
    void of_SpiritHoldsJungleLogs_PlanksOfOwnSpecies() {
        AcquireOrder order = AcquireOrder.of(
                "oak_planks", 8, Map.of("minecraft:jungle_log", 5, "minecraft:oak_log", 1, "minecraft:stick", 9));

        assertEquals(
                "jungle_planks",
                order.item(),
                "Доски — из своей самой обильной породы: иначе дух с брёвнами джунглей рубит дуб под дубовые доски");
        assertEquals(8, order.count(), "Своих досок джунглей нет: цель — ровно прирост");
    }

    @Test
    void of_NoOwnLogs_KeepsSettlementSpecies() {
        assertEquals(
                "birch_planks",
                AcquireOrder.of("minecraft:birch_planks", 4, Map.of()).item(),
                "Без своих брёвен порода — из запаса поселения, короткое имя как в скрипте");
    }

    @Test
    void of_HugeOwnStock_ClampedToScriptLimit() {
        assertEquals(
                CatalogLimits.COUNT_MAX,
                AcquireOrder.of("stick", 8, Map.of("minecraft:stick", CatalogLimits.COUNT_MAX))
                        .count(),
                "Цель не выходит за предел acquire: иначе скрипт отвергает параметры и задача падает на старте");
    }

    @Test
    void of_ZeroMore_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AcquireOrder.of("stick", 0, Map.of()),
                "Намерение без прироста — ошибка источника, а не пустая задача");
    }
}
