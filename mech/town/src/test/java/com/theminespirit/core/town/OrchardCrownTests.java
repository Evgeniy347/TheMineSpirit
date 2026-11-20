package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class OrchardCrownTests {
    private static final StageSite SITE = TownFixtures.flatSite();

    @Test
    void orchardCrown_LeavesOverPlot_True() {
        BlockCoord spot = SITE.orchardSaplings().get(0);
        int y = SITE.anchor().y();

        assertTrue(
                SITE.orchardCrown(spot.x() + 1, y + 5, spot.z()),
                "Крона выросшего дерева над соседней клеткой участка — часть сада: без этого ремонт снова "
                        + "срежет дерево по середине; При смене orchardCrown в StageSite проверь "
                        + "SchematicClearance и OrchardReadinessTests");
        assertTrue(
                SITE.orchardCrown(spot.x(), y + StageSite.ORCHARD_TREE_HEIGHT, spot.z()),
                "Верх кроны на пределе высоты дерева сада ещё защищён: иначе очистка снесёт макушку и дерево "
                        + "потеряет вид; При смене ORCHARD_TREE_HEIGHT проверь OrchardCrownTests и живой срез сада");
    }

    @Test
    void orchardCrown_AboveTreeHeight_False() {
        BlockCoord spot = SITE.orchardSaplings().get(0);
        int y = SITE.anchor().y();

        assertFalse(
                SITE.orchardCrown(spot.x(), y + StageSite.ORCHARD_TREE_HEIGHT + 1, spot.z()),
                "Выше кроны дерева сада клетка не сад: иначе очистка перестанет чистить дикий лес над садом; "
                        + "При смене границы высоты в orchardCrown проверь OrchardTreeTests и снос деревьев");
        assertFalse(
                SITE.orchardCrown(spot.x() + 40, y + 5, spot.z()),
                "Дерево за границей сада — не сад: без этого правила очистка щадила бы дикий лес далеко от "
                        + "участка; При смене orchardTerritory проверь StageSiteTests и OrchardCrownTests");
    }

    @Test
    void orchardTrunk_GrownTreeColumn_True() {
        BlockCoord spot = SITE.orchardSaplings().get(0);
        int y = SITE.anchor().y();

        assertTrue(
                SITE.orchardTree(spot.x(), y + 2, spot.z()),
                "Ствол выросшего дерева в колонке саженца — дерево сада: иначе строительная очистка снова "
                        + "срубит его по середине; При смене orchardTree проверь SchematicClearance и живой "
                        + "ремонт сада");
        assertFalse(
                SITE.orchardTree(spot.x() + 40, y + 2, spot.z()),
                "Ствол вне колонок саженцев — дикое дерево: иначе очистка не смогла бы расчистить лес внутри "
                        + "участка; При смене orchardTree проверь очистку сада и StrayRuleTests");
    }
}
