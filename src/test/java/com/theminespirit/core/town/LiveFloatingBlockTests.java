package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.SettlementPlot;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.live.LiveFloatingWorld;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class LiveFloatingBlockTests {
    private static final BlockCoord MID_TRUNK = new BlockCoord(-163, 90, -507);
    private static final BlockCoord CROWN_LEAF = new BlockCoord(-163, 98, -507);
    private static final BlockCoord FAR_CROWN_LEAF = new BlockCoord(-166, 96, -510);

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
    void liveFloatingCrown_Support_StandsOutsideTrunk() {
        List<BlockCoord> cells =
                List.of(new BlockCoord(-163, 85, -507), new BlockCoord(-162, 97, -506), new BlockCoord(-163, 98, -507));

        List<BlockCoord> supports = TreeSupport.candidates(cells);

        assertTrue(
                supports.stream()
                        .anyMatch(cell -> cell.x() < -163 || cell.x() > -162 || cell.z() < -507 || cell.z() > -506),
                "Опора обязана стоять снаружи ствола: столб в клетке бревна упирается в дерево и дух не "
                        + "поднимется. Кандидаты: " + supports);
        assertEquals(
                0,
                supports.stream()
                        .filter(cell -> cell.x() == -163 && cell.z() == -507)
                        .count(),
                "Клетка нижнего бревна не может быть стоянкой опоры: иначе pillar_up ставит блок в ствол");
    }

    @Test
    void liveFarCrownLeaf_FloatingTree_IsReported() throws IOException {
        LiveFloatingWorld world = LiveFloatingWorld.load();

        FloatingBlocks.Finding finding =
                FloatingBlocks.classify(FAR_CROWN_LEAF, world).orElseThrow();

        assertEquals(
                FloatingBlocks.Kind.TREE,
                finding.kind(),
                "Листва -166,96,-510 соединена с кроной только листвой, а не бревном: BFS обязан ходить "
                        + "по листве, иначе живая крона остаётся OTHER и в уборку не попадает (observe15 B7, 203 листа)");
    }

    @Test
    void liveStump_FewAdjacentLogs_IsIsolated() throws IOException {
        LiveFloatingWorld world = LiveFloatingWorld.load();

        assertTrue(
                TreeSupport.isolatedLog(new BlockCoord(-156, 70, -497), world::isLog),
                "Одинокий пенёк окупаемой вырубки не должен считаться постройкой игрока: под ним нет ствола");
        assertFalse(
                TreeSupport.isolatedLog(new BlockCoord(-163, 90, -507), world::isLog),
                "Бревно в 2x2 стволе не одиноко: его нельзя выдавать за отдельный пенёк и сносить как остаток");
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
