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
                "Разовый факел на уровне поселения ставится на свободный грунт"
                        + " Иначе тест adHoc_GroundFreeCell_Allowed перестанет ловить эту поломку. При смене adHoc_GroundFreeCell_Allowed проверь это утверждение.");
    }

    @Test
    void adHoc_GroundInsidePlan_Refused() {
        assertFalse(
                TorchRule.adHoc(true, LEVEL, LEVEL + 1, false),
                "Разовый факел не ставится в клетку плана: свет там задаёт схема, иначе он мешает перестройке"
                        + " При смене adHoc_GroundInsidePlan_Refused проверь это утверждение.");
    }

    @Test
    void adHoc_FenceTop_Allowed() {
        assertTrue(
                TorchRule.adHoc(true, LEVEL, LEVEL + 2, true),
                "Факел на верху забора разрешён: забор сам служит опорой"
                        + " Иначе тест adHoc_FenceTop_Allowed перестанет ловить эту поломку. При смене adHoc_FenceTop_Allowed проверь это утверждение.");
    }

    @Test
    void adHoc_FenceTopWithoutFence_Refused() {
        assertFalse(
                TorchRule.adHoc(false, LEVEL, LEVEL + 2, false),
                "Клетка над уровнем без забора — уже не факел на земле: только по схеме"
                        + " Иначе тест adHoc_FenceTopWithoutFence_Refused перестанет ловить эту поломку. При смене adHoc_FenceTopWithoutFence_Refused проверь это утверждение.");
    }

    @Test
    void adHoc_BelowSettlementLevel_Refused() {
        assertFalse(
                TorchRule.adHoc(false, LEVEL, LEVEL, false),
                "Факел в слое грунта не ставится: там камень или земля, а не место для света"
                        + " Иначе тест adHoc_BelowSettlementLevel_Refused перестанет ловить эту поломку. При смене adHoc_BelowSettlementLevel_Refused проверь это утверждение.");
    }

    @Test
    void adHoc_AboveFence_Refused() {
        assertFalse(
                TorchRule.adHoc(false, LEVEL, LEVEL + 3, true),
                "Выше верха забора разовый факел висит в воздухе: только по схеме"
                        + " Иначе тест adHoc_AboveFence_Refused перестанет ловить эту поломку. При смене adHoc_AboveFence_Refused проверь это утверждение.");
    }
}
