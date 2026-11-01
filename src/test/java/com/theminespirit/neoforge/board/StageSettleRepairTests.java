package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BuildGap;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.TownFixtures;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StageSettleRepairTests {
    private static final StageSite SITE = TownFixtures.flatSite();
    private static final BlockCoord MISSING = new BlockCoord(
            SITE.workYard().x() + 2, SITE.workYard().y(), SITE.workYard().z());
    private static final BlockCoord STANDING = new BlockCoord(
            SITE.workYard().x() + 1, SITE.workYard().y(), SITE.workYard().z());

    @Test
    void repairTarget_FenceWithOneMissingBlock_KeepsOnlyThatBlock() {
        StageWork work = new StageWork(
                SITE,
                List.of(
                        new PlannedBlock(STANDING, "minecraft:oak_fence"),
                        new PlannedBlock(MISSING, "minecraft:oak_fence")),
                List.of());
        TicketTarget target = TicketTarget.at(SITE.workYard()).withWork(work);
        BuildGap.Gap gap = new BuildGap.Gap(
                Map.of("oak_fence", 2),
                Map.of("oak_fence", 1),
                Map.of("oak_fence", 1),
                Map.of(MISSING, "minecraft:oak_fence"),
                List.of(MISSING + " ожидается oak_fence, стоит air"));

        TicketTarget repair = StageSettle.repairTarget(BoardCase.BUILD_FENCE, target, gap);

        assertEquals(
                1,
                repair.work().blocks().size(),
                "Ремонт ограды ставит только реально недостающий блок, а не весь сегмент: "
                        + repair.work().blocks());
        assertTrue(
                repair.work().blocks().get(0).at().equals(MISSING),
                "Оставлен именно недостающий блок " + MISSING + ": "
                        + repair.work().blocks());
    }
}
