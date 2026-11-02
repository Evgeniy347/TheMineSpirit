package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.TownFixtures;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class YardRepairPlanTests {
    private static final StageSite SITE = TownFixtures.flatSite();
    private static final BlockCoord HOLE = new BlockCoord(
            SITE.yard().x() + 1, SITE.anchor().y() - 1, SITE.yard().z() + 3);

    @Test
    void plan_YardFloorRepair_GoesToHoleNotUnreachableWorkYard() {
        StageWork work = new StageWork(
                SITE, List.of(new PlannedBlock(HOLE, "minecraft:dirt")), List.of(), BoardCase.STOCK_CHESTS.name());
        TicketTarget target = TicketTarget.at(SITE.workYard()).withWork(work);
        TaskNode plan = TicketPlans.plan(BoardCase.REPAIR_BUILDING, 1, target);

        List<BlockCoord> gotos = new ArrayList<>();
        collect(plan, gotos);

        assertEquals(
                List.of(),
                gotos,
                "Ремонт пола двора не идёт ни к workYard, ни в саму дыру: place_block сам подбирает стоянку "
                        + "рядом, иначе дух «застревает» на краю ямы");
    }

    private static void collect(TaskNode node, List<BlockCoord> gotos) {
        if (node instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, gotos));
        } else if (node instanceof FallbackNode fallback) {
            fallback.steps().forEach(step -> collect(step, gotos));
        } else if (node instanceof ScriptCall call && "goto".equals(call.script())) {
            gotos.add(new BlockCoord(
                    (Integer) call.params().get("x"), (Integer) call.params().get("y"), (Integer)
                            call.params().get("z")));
        }
    }
}
