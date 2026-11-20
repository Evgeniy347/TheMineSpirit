package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BuildGap;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.town.OrchardPlot;
import com.theminespirit.core.town.OrchardSites;
import com.theminespirit.core.town.StageSite;
import java.util.List;

final class OrchardRepairPost {
    private OrchardRepairPost() {}

    static boolean post(GraphPost post, String building) {
        StageSite site = post.scan.stage();
        List<String> plots = OrchardSites.plotIds(building, site.orchard() != null, site.extraOrchards());
        if (plots.isEmpty()) {
            return false;
        }
        for (String plot : plots) {
            repair(post, site, plot);
        }
        return true;
    }

    private static void repair(GraphPost post, StageSite site, String plot) {
        String wood = wood(site, plot);
        TicketTarget target = TicketTarget.at(site.orchardOrigin(plot))
                .withWork(StageBuilds.of(post.level, BoardCase.BUILD_ORCHARD, site, 0))
                .withWood(wood)
                .withBuilding(plot);
        BuildGap.Gap gap = FirstStepsRunner.gapAt(post.level, BoardCase.BUILD_ORCHARD, target, wood);
        if (!gap.confident() || gap.shortage() <= 0) {
            return;
        }
        StageRepair.post(
                post,
                BoardCase.BUILD_ORCHARD,
                target,
                new StageRepair.Gap(gap.shortage(), gap.total(), gap.missingItems()),
                plot);
    }

    private static String wood(StageSite site, String plot) {
        for (OrchardPlot extra : site.extraOrchards()) {
            if (extra.building().equals(plot)) {
                return extra.wood();
            }
        }
        return site.orchardWood();
    }
}
