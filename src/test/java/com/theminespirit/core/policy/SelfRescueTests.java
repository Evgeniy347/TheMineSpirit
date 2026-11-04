package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class SelfRescueTests {
    private static final BlockCoord FEET = new BlockCoord(-188, 67, -516);

    @Test
    void mayBreakAtFeet_BlockInsideSpirit_Allowed() {
        assertTrue(
                SelfRescue.mayBreakAtFeet(FEET, FEET),
                "Блок, внутри которого застрял дух, ломается при спасении: иначе забор из чёрного списка "
                        + "оставляет его замурованным навсегда");
    }

    @Test
    void mayBreakAtFeet_NeighbourBlock_NotAllowed() {
        assertFalse(
                SelfRescue.mayBreakAtFeet(new BlockCoord(-187, 67, -516), FEET),
                "Исключение только для клетки под духом: соседние блоки постройки защищены как прежде");
    }
}
