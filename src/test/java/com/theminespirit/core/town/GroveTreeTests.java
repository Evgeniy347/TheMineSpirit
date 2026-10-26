package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class GroveTreeTests {
    @Test
    void groveTree_SaplingColumn_PlannedFromSaplingToCrown() {
        StageSite site = TownFixtures.flatSite();
        BlockCoord spot = site.saplings().get(0);
        int y = site.anchor().y();

        assertEquals(
                List.of(true, true, false, false),
                List.of(
                        site.groveTree(spot.x(), y, spot.z()),
                        site.groveTree(spot.x(), y + 5, spot.z()),
                        site.groveTree(spot.x() + 1, y, spot.z()),
                        site.groveTree(spot.x(), y + StageSite.GROVE_TREE_HEIGHT + 1, spot.z())),
                "INC-89: саженец и выросшее дерево рощи — часть плана: аудит, снос и фоновая рубка их не трогают; "
                        + "соседняя колонка и высота выше кроны — уже не роща");
    }
}
