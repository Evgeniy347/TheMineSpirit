package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.RepairAudit;
import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.TownPlan;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;

public final class BuildingAuditInputs {
    private static final String HUT = "hut";
    private static final Set<String> SPECIAL =
            Set.of(HUT, "yard", "mine", "orchard", "orchard_extra", "fence", "torches", "streets", "clearing");
    private static final Set<String> STAGE_KEYS =
            Set.of(HUT, "mine", "chests", "fence", "lighting", "streets", "orchard_oak");

    record Audit(int total, int done, int extra, int unknown) {
        Audit {
            if (total < 0 || done < 0 || done > total || extra < 0 || unknown < 0) {
                throw new IllegalArgumentException("Аудит " + done + " из " + total + ", лишних " + extra
                        + ", неизвестных " + unknown + ": числа не отрицательны, сделанное не больше плана");
            }
        }

        Audit plus(Audit other) {
            return new Audit(total + other.total, done + other.done, extra + other.extra, unknown + other.unknown);
        }

        int missing() {
            return total - done;
        }
    }

    private BuildingAuditInputs() {}

    static boolean auditable(String planId) {
        if (planId == null || planId.isBlank()) {
            return false;
        }
        return SPECIAL.contains(planId)
                || planId.startsWith("orchard_")
                || StageCatalog.entry(planId).isPresent();
    }

    static String key(String planId) {
        Optional<String> graphId = StageCatalog.graphId(planId);
        if (graphId.isPresent()) {
            return graphId.orElseThrow();
        }
        return switch (planId) {
            case "yard" -> "chests";
            case "torches" -> "lighting";
            case "orchard", "orchard_extra" -> "orchard_oak";
            default -> planId;
        };
    }

    static Map<String, GraphFacts.Building> buildings(
            ServerLevel level,
            BoardService service,
            FirstStepsRunner.StageScan scan,
            Map<String, Built> states,
            Optional<TownPlan> town) {
        long now = service.board().now();
        Map<String, GraphFacts.Building> facts = new LinkedHashMap<>(GraphFactInputs.buildingFacts(states, town, scan));
        house(level, service, scan, facts, now);
        stage(level, scan, states, facts, now);
        town.ifPresent(
                plan -> plan.placements().forEach(place -> placement(level, plan, place, scan, states, facts, now)));
        return facts;
    }

    private static void house(
            ServerLevel level,
            BoardService service,
            FirstStepsRunner.StageScan scan,
            Map<String, GraphFacts.Building> facts,
            long now) {
        if (!service.board().ledger().has(BoardCase.BUILD_HOUSE, 1)) {
            return;
        }
        RepairAudit.Report report = WorldAreaInputs.houseReport(level, scan.stage(), now);
        if (report == null) {
            return;
        }
        BlockCoord origin = scan.stage().houseOrigin();
        facts.put(HUT, fact(HUT, 1, position(origin), BuildingAudit.report(report), true));
    }

    private static void stage(
            ServerLevel level,
            FirstStepsRunner.StageScan scan,
            Map<String, Built> states,
            Map<String, GraphFacts.Building> facts,
            long now) {
        put(facts, "mine", states, scan.stage().mineEntrance(), BuildingAudit.first(level, scan, BoardCase.BUILD_MINE));
        Audit chests = BuildingAudit.first(level, scan, BoardCase.STOCK_CHESTS)
                .plus(BuildingAudit.first(level, scan, BoardCase.CRAFT_FURNACE))
                .plus(BuildingAudit.floor(level, scan));
        put(facts, "chests", states, scan.stage().workYard(), chests);
        put(
                facts,
                "furnace",
                states,
                scan.stage().workYard(),
                BuildingAudit.first(level, scan, BoardCase.CRAFT_FURNACE));
        put(facts, "fence", states, scan.stage().anchor(), BuildingAudit.first(level, scan, BoardCase.BUILD_FENCE));
        put(facts, "lighting", states, scan.stage().anchor(), BuildingAudit.first(level, scan, BoardCase.LIGHTING));
        put(facts, "streets", states, scan.stage().anchor(), BuildingAudit.streets(level, scan));
        put(facts, "clearing", states, scan.stage().anchor(), BuildingAudit.clearing(level, scan, now));
        put(
                facts,
                "orchard_oak",
                states,
                scan.stage().orchardOrigin(),
                BuildingAudit.first(level, scan, BoardCase.BUILD_ORCHARD));
    }

    private static void placement(
            ServerLevel level,
            TownPlan plan,
            Placement place,
            FirstStepsRunner.StageScan scan,
            Map<String, Built> states,
            Map<String, GraphFacts.Building> facts,
            long now) {
        if (!auditable(place.id())) {
            return;
        }
        String key = key(place.id());
        if (STAGE_KEYS.contains(key)) {
            return;
        }
        boolean built = built(states, key, place.id());
        GraphFacts.Building base = facts.get(key);
        int currentLevel = built || (base != null && base.level() > 0) ? 1 : 0;
        GraphFacts.Position at =
                new GraphFacts.Position(place.x(), plan.anchor().y(), place.z());
        Optional<StageCatalog.Entry> entry = StageCatalog.entry(place.id());
        Audit audit = entry.map(value -> BuildingAudit.scheme(level, value.scheme(), at, scan.wood(), now))
                .orElseGet(() -> BuildingAudit.stageOrchard(level, at, scan.wood(), now));
        facts.put(key, fact(key, currentLevel, at, audit, built));
    }

    private static boolean built(Map<String, Built> states, String key, String planId) {
        return states.get(key) == Built.BUILT || states.get(planId) == Built.BUILT;
    }

    private static void put(
            Map<String, GraphFacts.Building> facts, String key, Map<String, Built> states, BlockCoord at, Audit audit) {
        GraphFacts.Building base = facts.get(key);
        boolean built = states.get(key) == Built.BUILT;
        int level = built || (base != null && base.level() > 0) ? 1 : 0;
        facts.put(key, fact(key, level, position(at), audit, built));
    }

    private static GraphFacts.Position position(BlockCoord at) {
        return new GraphFacts.Position(at.x(), at.y(), at.z());
    }

    static GraphFacts.Building fact(String id, int level, GraphFacts.Position at, Audit audit, boolean built) {
        if (audit.unknown() > 0) {
            return new GraphFacts.Building(id, level, 0, 0, 0, 0, at, true);
        }
        int percent = audit.total() == 0 ? (built ? 100 : 0) : Math.min(100, audit.done() * 100 / audit.total());
        int missing = audit.missing();
        int extra = audit.extra();
        int damage = built ? missing + extra : 0;
        return new GraphFacts.Building(id, level, percent, damage, missing, extra, at, false);
    }
}
