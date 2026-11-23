package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClearRiseTests {
    @Test
    void above_AtSettlementLevel_Denied() {
        assertFalse(
                ClearRise.above(64, 64),
                "На уровне поселения подмости расчистки запрещены: иначе дух топчется на месте, ставит столб и "
                        + "тут же его разбирает. Ниже уровня участок выравнивают послойно. При смене "
                        + "above_AtSettlementLevel_Denied проверь границу уровня поселения.");
    }

    @Test
    void above_OneAboveLevel_Allowed() {
        assertTrue(
                ClearRise.above(65, 64),
                "Выше уровня поселения подмости расчистки нужны: иначе дух не достанет верхние клетки срезки "
                        + "и оставит их неубранными. При смене above_OneAboveLevel_Allowed проверь границу "
                        + "уровня поселения.");
    }

    @Test
    void above_UnknownSettlementLevel_Allowed() {
        assertTrue(
                ClearRise.above(0, Integer.MIN_VALUE),
                "Без обмеренного участка запрет не действует: иначе до разметки поселения расчистка теряет "
                        + "подмости и клетки выше духа остаются неубранными. При смене "
                        + "above_UnknownSettlementLevel_Allowed проверь запасной путь без уровня.");
    }
}
