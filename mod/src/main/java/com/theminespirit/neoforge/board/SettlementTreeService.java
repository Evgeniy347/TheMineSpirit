package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.SettlementStage;
import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.StageRules;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.SettlementPlot;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.ui.SettlementTree;
import com.theminespirit.core.ui.SettlementTreeFacts;
import com.theminespirit.core.ui.SettlementTreeIcons;
import com.theminespirit.core.ui.SettlementTrees;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;

public final class SettlementTreeService {
    private static final int CACHE_TICKS = 20;
    private static final Map<String, Cached> CACHE = new LinkedHashMap<>();

    private record Cached(long bucket, int stage, SettlementTree tree) {}

    private SettlementTreeService() {}

    public static SettlementTree tree(ServerLevel level, int requestedStage) {
        StageRules rules = DataTables.INSTANCE.stages().current();
        int stage = Math.max(1, Math.min(requestedStage, rules.stages().size()));
        long bucket = level.getGameTime() / CACHE_TICKS;
        String key = level.dimension().location() + "|" + stage;
        Cached cached = CACHE.get(key);
        if (cached != null && cached.bucket() == bucket && cached.stage() == stage) {
            return cached.tree();
        }
        SettlementTree tree = build(level, rules, stage);
        CACHE.put(key, new Cached(bucket, stage, tree));
        return tree;
    }

    private static SettlementTree build(ServerLevel level, StageRules rules, int stage) {
        BoardService service = BoardRegistry.get(level);
        int currentStage = SettlementStage.of(service.board().ledger());
        SettlementTreeFacts facts = facts(level, rules, stage, service);
        return SettlementTrees.build(rules, facts, SettlementTreeIcons.current(), currentStage, stage);
    }

    private static SettlementTreeFacts facts(ServerLevel level, StageRules rules, int stage, BoardService service) {
        SettlementPlotStore store = SettlementPlotStore.get(level);
        Optional<StageSite> site = store.site();
        Optional<TownPlan> town = store.town();
        Optional<SettlementPlot> plot = store.plot();
        if (site.isEmpty() || town.isEmpty() || plot.isEmpty()) {
            return SettlementTreeFacts.empty();
        }
        StageSite place = site.orElseThrow();
        String wood = plot.orElseThrow().wood();
        long now = service.board().now();
        Map<String, SettlementTreeFacts.Fact> facts = new LinkedHashMap<>();
        StageRules.Stage rule = rules.stages().get(stage - 1);
        for (Map.Entry<BoardCase, Integer> entry : rule.buildings().entrySet()) {
            SettlementTreeFacts.Fact fact = caseFact(level, place, wood, entry.getKey(), entry.getValue(), now);
            facts.put(SettlementTreeFacts.boardKey(entry.getKey(), entry.getValue()), fact);
        }
        for (String planId : planIds(rule)) {
            planFacts(level, town.orElseThrow(), place, wood, planId, now, facts);
        }
        return new SettlementTreeFacts(facts);
    }

    private static Set<String> planIds(StageRules.Stage rule) {
        Set<String> ids = new LinkedHashSet<>();
        ids.addAll(rule.plan());
        ids.addAll(rule.requires().keySet());
        ids.addAll(rule.optional().keySet());
        return ids;
    }

    private static void planFacts(
            ServerLevel level,
            TownPlan town,
            StageSite place,
            String wood,
            String planId,
            long now,
            Map<String, SettlementTreeFacts.Fact> facts) {
        int maxLevel = StageCatalog.maxLevel(planId);
        for (int buildingLevel = 1; buildingLevel <= maxLevel; buildingLevel++) {
            Optional<String> scheme = StageCatalog.scheme(planId, buildingLevel);
            Optional<BlockCoord> origin =
                    StageCatalog.origin(planId, town, place.anchor().y());
            if (scheme.isEmpty() || origin.isEmpty()) {
                return;
            }
            BlockCoord at = origin.orElseThrow();
            BuildingAuditInputs.Audit audit = BuildingAudit.scheme(
                    level, scheme.orElseThrow(), new GraphFacts.Position(at.x(), at.y(), at.z()), wood, now);
            SettlementTreeFacts.Fact fact = fact(audit, origin.orElseThrow());
            facts.put(SettlementTreeFacts.planKey(planId, buildingLevel), fact);
            if (!fact.ready()) {
                return;
            }
        }
    }

    private static SettlementTreeFacts.Fact caseFact(
            ServerLevel level, StageSite place, String wood, BoardCase boardCase, int buildingLevel, long now) {
        BuildingAuditInputs.Audit audit = switch (boardCase) {
            case CLEAR_GROUNDS -> BuildingAudit.clearing(level, place, now);
            case BUILD_STREETS -> BuildingAudit.streets(level, place, wood);
            default -> {
                FirstStepsRunner.Placed placed = FirstStepsRunner.audit(level, boardCase, buildingLevel, place, wood);
                yield new BuildingAuditInputs.Audit(
                        placed.total(), placed.done(), Map.of(), Map.of(), 0, placed.unknown());
            }
        };
        return fact(audit, casePosition(place, boardCase));
    }

    private static SettlementTreeFacts.Fact fact(BuildingAuditInputs.Audit audit, BlockCoord at) {
        if (audit.unknown() > 0) {
            return new SettlementTreeFacts.Fact(0, false, true, Map.of(), position(at));
        }
        int percent = audit.total() == 0 ? 0 : Math.min(100, audit.done() * 100 / audit.total());
        boolean ready = audit.total() > 0 && audit.done() >= audit.total();
        return new SettlementTreeFacts.Fact(percent, ready, false, audit.missing(), position(at));
    }

    private static SettlementTree.Position position(BlockCoord at) {
        return new SettlementTree.Position(at.x(), at.y(), at.z());
    }

    private static BlockCoord casePosition(StageSite place, BoardCase boardCase) {
        return switch (boardCase) {
            case BUILD_MINE -> place.mineEntrance();
            case BUILD_ORCHARD -> place.orchardOrigin();
            case BUILD_HOUSE -> place.houseOrigin();
            case STOCK_CHESTS -> place.workYard();
            default -> place.anchor();
        };
    }
}
