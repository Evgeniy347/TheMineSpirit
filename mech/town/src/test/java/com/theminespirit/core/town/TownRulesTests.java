package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TownRulesTests {
    @Test
    void load_SlopeMax_FitsLevelingShift() {
        TownRules rules = TownRules.load();

        assertTrue(
                rules.ground().slopeMax() <= PlotLeveling.MAX_SHIFT,
                "Предел уклона площадки " + rules.ground().slopeMax() + " больше сдвига выравнивания "
                        + PlotLeveling.MAX_SHIFT + ": постройка встанет на уступ, который выравнивание уже не снимет");
    }
}
