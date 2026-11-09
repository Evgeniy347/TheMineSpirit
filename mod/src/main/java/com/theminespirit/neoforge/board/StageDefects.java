package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.StageView;
import com.theminespirit.core.town.StageSite;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

final class StageDefects {
    private StageDefects() {}

    static StageView withCounts(ServerLevel level, StageView base) {
        List<StageView.Requirement> requirements =
                new ArrayList<>(base.requirements().size());
        for (StageView.Requirement requirement : base.requirements()) {
            Map<String, Integer> counts = counts(level, requirement.id());
            requirements.add(new StageView.Requirement(
                    requirement.stage(),
                    requirement.id(),
                    requirement.level(),
                    requirement.closed(),
                    requirement.world(),
                    requirement.optional(),
                    counts));
        }
        return new StageView(base.stage(), base.stages(), base.missing(), requirements);
    }

    static Map<String, Integer> counts(ServerLevel level, String id) {
        if (!BoardCase.CLEAR_GROUNDS.name().equals(id)) {
            return Map.of();
        }
        Optional<StageSite> site =
                SettlementPlotStore.get(level).site(SettlementPlotStore.currentStage(BoardRegistry.get(level)));
        if (site.isEmpty()) {
            return Map.of();
        }
        BuildingAuditInputs.Audit audit = BuildingAudit.clearing(
                level, site.orElseThrow(), BoardRegistry.get(level).board().now());
        if (audit.unknown() > 0) {
            return Map.of();
        }
        BuildingAuditInputs.Defects defects = audit.defects();
        return Map.of("holes", defects.holes(), "mounds", defects.mounds(), "pillars", defects.pillars());
    }
}
