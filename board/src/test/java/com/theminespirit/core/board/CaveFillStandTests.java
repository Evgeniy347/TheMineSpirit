package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownFixtures;
import org.junit.jupiter.api.Test;

class CaveFillStandTests {
    private static final StageSite SITE = TownFixtures.flatSite();

    @Test
    void fillStandAllowed_CaveUnderPad_Refused() {
        BlockCoord pocket = new BlockCoord(
                SITE.yard().x(), SITE.anchor().y() - 7, SITE.yard().z());
        assertFalse(
                SITE.fillStandAllowed(pocket, false),
                "Подсыпка не встаёт в пещеру без неба под площадкой: карман " + pocket + " (INC-72)");
    }

    @Test
    void fillStandAllowed_OpenPitOrOutside_Allowed() {
        BlockCoord pit = new BlockCoord(
                SITE.yard().x(), SITE.anchor().y() - 3, SITE.yard().z());
        BlockCoord far =
                new BlockCoord(SITE.enclosure().minX() - 30, 40, SITE.anchor().z());
        assertTrue(SITE.fillStandAllowed(pit, true), "Открытая низина площадки с небом — законная стоянка (INC-66)");
        assertTrue(
                SITE.fillStandAllowed(far, false),
                "Вне площадок правило не действует: подсыпка в шахте и ямах далеко от поселения");
    }
}
