package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.OrchardSites;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.SettlementPlot;
import com.theminespirit.core.town.TerrainGrid;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownPlanner;
import com.theminespirit.core.town.TownRules;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class TownSurvey {
    static final String JOURNAL_ID = "town";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static BlockCoord warnedAnchor;

    private TownSurvey() {}

    static TownPlan town(
            ServerLevel level, BoardService service, SettlementPlotStore store, BlockCoord center, long now) {
        TownRules rules = DataTables.INSTANCE.town().current();
        List<List<String>> stages = DataTables.INSTANCE.stages().current().plans();
        Optional<TownPlan> stored =
                store.town().filter(plan -> near(plan.anchor(), center) && TownPlanner.keepStored(plan));
        if (stored.isPresent()) {
            TownPlan kept = stored.orElseThrow();
            if (!TownPlanner.laidWith(kept, rules) && !kept.anchor().equals(warnedAnchor)) {
                warnedAnchor = kept.anchor();
                LOGGER.warn("[plot] план размечен прежним обмером: места построек сохраняются, новый обмер не переносит"
                        + " origin; расширение — только новыми участками");
            }
            return extend(level, store, stored.orElseThrow(), rules, stages, service);
        }
        Optional<SettlementPlot> old = store.plot();
        TerrainGrid grid = GroundProbe.terrain(level, center, rules.ground().probeRadius());
        TownPlan plan = TownPlanner.plan(grid, center, rules, stages);
        store.fixTown(plan);
        plan = extend(level, store, plan, rules, stages, service);
        String cause = old.isPresent()
                ? "старый участок у " + old.orElseThrow().anchor() + " заменён планом"
                : "план поселения размечен";
        LOGGER.info(
                "[plot] {}: якорь {}, площадь {}, построек {}, столбов ограды {}, калиток {}, не поместилось {}",
                cause,
                center,
                plan.area(),
                plan.placements().size(),
                plan.fence().posts().size(),
                plan.fence().gates().size(),
                plan.unplaced());
        service.journal()
                .recordEntry(BoardLog.note(
                        JOURNAL_ID,
                        BoardCase.CLEAR_GROUNDS,
                        now,
                        List.of(
                                cause,
                                "x=" + center.x(),
                                "z=" + center.z(),
                                "area=" + plan.area(),
                                "buildings=" + plan.placements().size(),
                                "unplaced=" + String.join(",", plan.unplaced())),
                        null,
                        ConfigStamp.current()));
        return plan;
    }

    private static TownPlan extend(
            ServerLevel level,
            SettlementPlotStore store,
            TownPlan plan,
            TownRules rules,
            List<List<String>> stages,
            BoardService service) {
        TownPlan current = plan;
        List<String> demanded = demanded(service, current);
        boolean missing = stages.get(0).stream()
                .filter(id -> !TownPlanner.LINE_ELEMENTS.contains(id))
                .anyMatch(id -> plan.placement(id).isEmpty() && !plan.unplaced().contains(id));
        boolean resized = TownPlanner.staleSizes(current, rules);
        if (demanded.isEmpty() && !missing && !resized) {
            return current;
        }
        TerrainGrid grid =
                GroundProbe.terrain(level, current.anchor(), rules.ground().probeRadius());
        if (resized) {
            TownPlan adapted = TownPlanner.readapt(grid, current, rules, built(service, current));
            if (!adapted.equals(current)) {
                store.refitTown(adapted);
                LOGGER.info("[plot] участки подогнаны под новые схемы: {}", adapted.unplaced());
                current = adapted;
            }
        }
        TownPlan next = TownPlanner.plant(grid, current, rules, stages, demanded, 1);
        if (!next.equals(current)) {
            store.refitTown(next);
            LOGGER.info("[plot] план расширен: сады {}, не поместилось {}", demanded, next.unplaced());
        }
        return next;
    }

    private static Set<String> built(BoardService service, TownPlan plan) {
        Set<String> built = new HashSet<>();
        for (Placement place : plan.placements()) {
            if (service.board().ledger().hasPlan(place.id(), 1)) {
                built.add(place.id());
            }
        }
        return built;
    }

    private static List<String> demanded(BoardService service, TownPlan plan) {
        List<String> wanted = new ArrayList<>();
        Map<String, Integer> stock = service.stock().available();
        for (SupplyGraph.Trigger trigger : DataTables.INSTANCE.graph().current().triggers()) {
            if (!OrchardSites.isExtra(trigger.build()) || stock.getOrDefault(trigger.have(), 0) <= 0) {
                continue;
            }
            String site = OrchardSites.siteId(trigger.build());
            if (plan.placement(site).isEmpty() && !plan.unplaced().contains(site)) {
                wanted.add(site);
            }
        }
        return wanted;
    }

    private static boolean near(BlockCoord anchor, BlockCoord center) {
        return Math.abs(anchor.x() - center.x()) <= SettlementPlot.DRIFT
                && Math.abs(anchor.z() - center.z()) <= SettlementPlot.DRIFT;
    }
}
