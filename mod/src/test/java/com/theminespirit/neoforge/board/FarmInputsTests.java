package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownFixtures;
import org.junit.jupiter.api.Test;

class FarmInputsTests {
    @Test
    void field_WheatFieldAwayFromHome_UsesPlacementCenter() {
        StageSite site = TownFixtures.flatSite();
        BlockCoord home = new BlockCoord(-166, site.anchor().y(), -392);
        Placement field = site.fields().stream()
                .filter(placement -> "wheat_field".equals(placement.id()))
                .findFirst()
                .orElseThrow();
        BlockCoord expected = new BlockCoord(field.centerX(), site.anchor().y(), field.centerZ());

        BlockCoord actual = FarmInputs.field(site, home);

        assertNotEquals(
                home,
                actual,
                "Домашняя грядка не обязана стоять у точки home: после переноса плана она может быть в 80 блоках, "
                        + "иначе скан около дома не увидит ни пшеницы, ни пашни и ферма снова встанет. "
                        + "При смене FarmInputs.field проверь "
                        + "live wheat_field (-134..-119, -467..-452), FarmInputs.crops и FarmPost.");
        assertEquals(
                expected,
                actual,
                "Работа фермы идёт в центр участка wheat_field: иначе till/plant/harvest ищут землю у дома "
                        + "и повторно падают с нулевым прогрессом. При смене FarmInputs.field проверь FarmPost.event, "
                        + "FarmPost.special и ActivityPlans.");
    }
}
