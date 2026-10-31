package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.SettlementPlot;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.live.LiveFloatingWorld;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class LiveFloatingBlockTests {
    private static final BlockCoord MID_TRUNK = new BlockCoord(-163, 90, -507);
    private static final BlockCoord CROWN_LEAF = new BlockCoord(-163, 98, -507);

    @Test
    void liveCrownTop_PlotSurvey_StaysInsideAuditWindow() {
        SettlementPlot plot = new SettlementPlot(new BlockCoord(-169, 69, -500), 64, 74, 4, "oak");

        assertTrue(
                FloatingWindow.topOf(plot) >= 98,
                "Аудит обрезан на y=" + FloatingWindow.topOf(plot) + ": живая крона -163,98,-507 выше потолка "
                        + "участка (max=74) и в него не попадает — поэтому владелец видит деревья, а spirit audit "
                        + "floating о них молчит");
    }

    @Test
    void liveMidTrunk_FloatingTree_IsReported() throws IOException {
        LiveFloatingWorld world = LiveFloatingWorld.load();

        FloatingBlocks.Finding finding =
                FloatingBlocks.classify(MID_TRUNK, world).orElseThrow();

        assertEquals(
                FloatingBlocks.Kind.TREE,
                finding.kind(),
                "Бревно -163,90,-507 висит над срубленным низом (y=69..84), но аудит его не видит: "
                        + "проверяется только нижний блок, а верх ствола считается «опёртым» на нижнее бревно");
    }

    @Test
    void liveCrownLeaf_FloatingTree_IsReported() throws IOException {
        LiveFloatingWorld world = LiveFloatingWorld.load();

        FloatingBlocks.Finding finding =
                FloatingBlocks.classify(CROWN_LEAF, world).orElseThrow();

        assertEquals(
                FloatingBlocks.Kind.TREE,
                finding.kind(),
                "Листва -163,98,-507 осталась висеть после срубленного низа, но считается «природной» "
                        + "и не попадает ни в аудит, ни в уборку");
    }
}
