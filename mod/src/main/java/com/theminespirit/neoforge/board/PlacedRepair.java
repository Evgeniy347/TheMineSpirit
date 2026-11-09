package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BuildGap;
import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.TownPlan;
import java.util.Optional;

final class PlacedRepair {
    private PlacedRepair() {}

    static Optional<TicketTarget> target(GraphPost post, String id, TicketTarget base) {
        Optional<StageCatalog.Entry> entry = StageCatalog.entry(id);
        Optional<TownPlan> town = SettlementPlotStore.get(post.level).town();
        if (entry.isEmpty() || town.isEmpty()) {
            return Optional.empty();
        }
        return StageCatalog.origin(
                        id, town.orElseThrow(), post.scan.stage().anchor().y())
                .map(origin -> target(origin, entry.orElseThrow().scheme(), id, base));
    }

    static TicketTarget target(BlockCoord origin, String scheme, String id, TicketTarget base) {
        TicketTarget target = TicketTarget.at(origin).withScheme(scheme).withBuilding(id);
        if (base.work() != null) {
            target = target.withWork(base.work());
        }
        return base.wood() == null ? target : target.withWood(base.wood());
    }

    static BuildGap.Gap gap(GraphPost post, TicketTarget target, String wood) {
        return BuildGap.trace(
                TicketPlans.plan(BoardCase.BUILD_PLACED, 1, target),
                wood,
                FirstStepsRunner.probe(post.level),
                FirstStepsRunner.SCHEMES);
    }
}
