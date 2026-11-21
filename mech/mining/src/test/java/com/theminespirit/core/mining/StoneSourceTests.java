package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StoneSourceTests {
    @Test
    void allowed_SettledStoneUnderSky_Refused() {
        assertFalse(
                StoneSource.allowed("minecraft:stone", true, true),
                "камень под открытым небом дух поселения не копает: булыжник — только из шахты (владелец 23.10)"
                        + " Иначе тест allowed_SettledStoneUnderSky_Refused перестанет ловить эту поломку. При смене allowed_SettledStoneUnderSky_Refused проверь это утверждение.");
    }

    @Test
    void allowed_SettledStoneUnderground_Allowed() {
        assertTrue(
                StoneSource.allowed("minecraft:cobblestone", false, true),
                "под землёй — шахта: там камень добывается"
                        + " Иначе тест allowed_SettledStoneUnderground_Allowed перестанет ловить эту поломку. При смене allowed_SettledStoneUnderground_Allowed проверь это утверждение.");
    }

    @Test
    void allowed_WildSpiritOrOtherBlock_Allowed() {
        assertTrue(
                StoneSource.allowed("minecraft:stone", true, false)
                        && StoneSource.allowed("minecraft:oak_log", true, true),
                "запрет касается только камня и только духов поселения: команды и сцены без дома копают где велено"
                        + " Иначе тест allowed_WildSpiritOrOtherBlock_Allowed перестанет ловить эту поломку. При смене allowed_WildSpiritOrOtherBlock_Allowed проверь это утверждение.");
    }
}
