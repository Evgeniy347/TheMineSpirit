package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class CaveFillStandTests {
    private static final BlockCoord ANCHOR = new BlockCoord(-170, 71, -487);

    @Test
    void fillStandAllowed_CaveUnderPlot_Refused() {
        assertFalse(
                SettlementLayout.fillStandAllowed(ANCHOR, new BlockCoord(-165, 64, -489), false),
                "Подсыпка не встаёт в пещеру без неба под участком: карман (-165,64,-489) в 5-й раз (INC-72)");
    }

    @Test
    void fillStandAllowed_OpenPitOrOutside_Allowed() {
        assertTrue(
                SettlementLayout.fillStandAllowed(ANCHOR, new BlockCoord(-171, 68, -483), true),
                "Открытая низина участка с небом — законная стоянка подсыпки (INC-66)");
        assertTrue(
                SettlementLayout.fillStandAllowed(ANCHOR, new BlockCoord(-100, 40, -487), false),
                "Вне участка правило не действует: подсыпка в шахте и ямах далеко от поселения");
    }
}
