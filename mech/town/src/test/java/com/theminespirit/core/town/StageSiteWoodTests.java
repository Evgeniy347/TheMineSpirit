package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StageSiteWoodTests {
    private static final StageSite BASE = TownFixtures.flatSite();

    @Test
    void canonicalConstructor_UnknownWood_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StageSite(
                        BASE.stage(),
                        BASE.anchor(),
                        BASE.hut(),
                        BASE.yard(),
                        BASE.mine(),
                        BASE.minePlot(),
                        BASE.orchard(),
                        "bamboo",
                        BASE.orchardSaplings(),
                        BASE.extraOrchards(),
                        BASE.fields(),
                        BASE.placements(),
                        BASE.level(),
                        BASE.zone(),
                        BASE.streets(),
                        BASE.enclosure(),
                        BASE.fenceStrip(),
                        BASE.ring()),
                "Порода дерева проверяется в самом конструкторе места этапа: обход через StageSites.of "
                        + "пропускал прямую сборку и нёс негодную породу в схемы");
    }

    @Test
    void canonicalConstructor_NullWood_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> TownFixtures.flatSite(null),
                "Пустая порода отвергается там же, где остальные обязательные поля места этапа");
    }
}
