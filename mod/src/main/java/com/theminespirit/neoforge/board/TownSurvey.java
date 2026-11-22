package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.api.ConfigStamp;
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
import java.util.HashMap;
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
    private static final Set<String> staleIgnored = new HashSet<>();
    private static final long EXTEND_COOLDOWN = 1200L;
    private static final HashMap<BlockCoord, Long> lastExtend = new HashMap<>();

    private TownSurvey() {}

    static TownPlan town(
            ServerLevel level, BoardService service, SettlementPlotStore store, BlockCoord center, long now) {
        TownRules rules = DataTables.INSTANCE.town().current();
        List<List<String>> stages = DataTables.INSTANCE.stages().current().plans();
        Optional<TownPlan> stored =
                store.town().filter(plan -> near(plan.anchor(), center) && TownPlanner.keepStored(plan));
        if (stored.isPresent() && TownPlanner.laidWith(stored.orElseThrow(), rules)) {
            return extend(level, store, stored.orElseThrow(), rules, stages, service, now);
        }
        if (stored.isPresent() && !stored.orElseThrow().anchor().equals(warnedAnchor)) {
            warnedAnchor = stored.orElseThrow().anchor();
            LOGGER.warn("[plot] план размечен прежними правилами: участки и обмер сменились, план пересобран заново");
            service.journal()
                    .recordEntry(BoardLog.note(
                            JOURNAL_ID,
                            BoardCase.CLEAR_GROUNDS,
                            now,
                            List.of("план устарел: обмер или размеры участков сменились", "пересборка плана"),
                            null,
                            ConfigStamp.current()));
        }
        Optional<SettlementPlot> old = store.plot();
        TerrainGrid grid = GroundProbe.terrain(level, center, rules.ground().probeRadius());
        TownPlan plan = TownPlanner.plan(grid, center, rules, stages);
        store.fixTown(plan);
        plan = extend(level, store, plan, rules, stages, service, now);
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
            BoardService service,
            long now) {
        TownPlan current = plan;
        List<String> demanded = demanded(service, current);
        boolean missing = stages.get(0).stream()
                .filter(id -> !TownPlanner.LINE_ELEMENTS.contains(id))
                .anyMatch(id -> plan.placement(id).isEmpty() && !plan.unplaced().contains(id));
        boolean resized = TownPlanner.staleSizes(current, rules) && !staleIgnored.contains(staleKey(current, rules));
        if (demanded.isEmpty() && !missing && !resized) {
            return current;
        }
        Long last = lastExtend.get(current.anchor());
        if (last != null && now - last < EXTEND_COOLDOWN) {
            return current;
        }
        lastExtend.put(current.anchor(), now);
        TerrainGrid grid =
                GroundProbe.terrain(level, current.anchor(), rules.ground().probeRadius());
        if (resized) {
            TownPlan adapted = TownPlanner.readapt(grid, current, rules, built(service, current));
            if (!adapted.equals(current)) {
                if (sameLots(adapted, current)) {
                    staleIgnored.add(staleKey(current, rules));
                    resized = false;
                } else {
                    store.refitTown(adapted);
                    LOGGER.info("[plot] участки подогнаны под новые схемы: {}", adapted.unplaced());
                    current = adapted;
                }
            }
        }
        TownPlan next = TownPlanner.plant(grid, current, rules, stages, demanded, 1);
        if (!next.equals(current)) {
            store.refitTown(next);
            LOGGER.info("[plot] план расширен: сады {}, не поместилось {}", demanded, next.unplaced());
        }
        return next;
    }

    private static boolean sameLots(TownPlan first, TownPlan second) {
        return first.placements().equals(second.placements())
                && first.unplaced().equals(second.unplaced());
    }

    private static String staleKey(TownPlan plan, TownRules rules) {
        return plan.anchor() + "|" + rules;
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
        SupplyGraph graph = DataTables.INSTANCE.graph().current();
        Set<String> built = built(service, plan);
        for (SupplyGraph.Trigger trigger : graph.triggers()) {
            if (!OrchardSites.isExtra(trigger.build())) {
                continue;
            }
            String base = OrchardSites.baseBuilding(trigger.build());
            int saplings = stock.getOrDefault(trigger.have(), 0);
            List<Placement> plots = plotsOf(plan, base);
            if (plots.isEmpty()) {
                if (saplings > 0 && !plan.unplaced().contains(base)) {
                    wanted.add(base);
                }
                continue;
            }
            boolean previousBuilt = plots.stream().allMatch(place -> built.contains(place.id()));
            int perPlot = saplingsPerPlot(graph, base, trigger.have());
            if (!OrchardSites.wantAnother(previousBuilt, saplings, perPlot)) {
                continue;
            }
            String next = OrchardSites.numbered(base, planNumber(plan, base) + 1);
            if (plan.placement(next).isEmpty() && !plan.unplaced().contains(next)) {
                wanted.add(next);
            }
        }
        return wanted;
    }

    private static List<Placement> plotsOf(TownPlan plan, String base) {
        List<Placement> plots = new ArrayList<>();
        if (OrchardSites.MAIN_BUILDING.equals(base)) {
            plan.placement(OrchardSites.MAIN_PLACEMENT).ifPresent(plots::add);
        }
        for (Placement place : plan.placements()) {
            if (OrchardSites.isExtra(place.id()) && base.equals(OrchardSites.baseBuilding(place.id()))) {
                plots.add(place);
            }
        }
        return plots;
    }

    private static int planNumber(TownPlan plan, String base) {
        int number = OrchardSites.MAIN_BUILDING.equals(base)
                        && plan.placement(OrchardSites.MAIN_PLACEMENT).isPresent()
                ? 1
                : 0;
        for (Placement place : plan.placements()) {
            if (OrchardSites.isExtra(place.id()) && base.equals(OrchardSites.baseBuilding(place.id()))) {
                number = Math.max(number, OrchardSites.number(place.id()));
            }
        }
        return number;
    }

    private static int saplingsPerPlot(SupplyGraph graph, String base, String sapling) {
        SupplyGraph.Building building = graph.buildings().get(base);
        if (building == null) {
            return 1;
        }
        return Math.max(1, building.materials().getOrDefault(sapling, 1));
    }

    private static boolean near(BlockCoord anchor, BlockCoord center) {
        return Math.abs(anchor.x() - center.x()) <= SettlementPlot.DRIFT
                && Math.abs(anchor.z() - center.z()) <= SettlementPlot.DRIFT;
    }
}
