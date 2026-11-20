package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TorchRuleTests {
    private static final int LEVEL = 67;

    @Test
    void adHoc_GroundFreeCell_Allowed() {
        assertTrue(
                TorchRule.adHoc(false, LEVEL, LEVEL + 1, false),
                "Разовый факел на уровне поселения ставится на свободный грунт");
    }

    @Test
    void adHoc_GroundInsidePlan_Refused() {
        assertFalse(
                TorchRule.adHoc(true, LEVEL, LEVEL + 1, false),
                "Разовый факел не ставится в клетку плана: свет там задаёт схема, иначе он мешает перестройке");
    }

    @Test
    void adHoc_FenceTop_Allowed() {
        assertTrue(
                TorchRule.adHoc(true, LEVEL, LEVEL + 2, true),
                "Факел на верху забора разрешён: забор сам служит опорой");
    }

    @Test
    void adHoc_FenceTopWithoutFence_Refused() {
        assertFalse(
                TorchRule.adHoc(false, LEVEL, LEVEL + 2, false),
                "Клетка над уровнем без забора — уже не факел на земле: только по схеме");
    }

    @Test
    void adHoc_BelowSettlementLevel_Refused() {
        assertFalse(
                TorchRule.adHoc(false, LEVEL, LEVEL, false),
                "Факел в слое грунта не ставится: там камень или земля, а не место для света");
    }

    @Test
    void adHoc_AboveFence_Refused() {
        assertFalse(
                TorchRule.adHoc(false, LEVEL, LEVEL + 3, true),
                "Выше верха забора разовый факел висит в воздухе: только по схеме");
    }
}
