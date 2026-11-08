package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.RepairAudit;
import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.TownPlan;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;

public final class BuildingAuditInputs {
    private static final String HUT = "hut";
    private static final Set<String> SPECIAL =
            Set.of(HUT, "yard", "mine", "orchard", "orchard_extra", "fence", "torches", "streets", "clearing");
    private static final Set<String> STAGE_KEYS =
            Set.of(HUT, "mine", "chests", "fence", "lighting", "streets", "orchard_oak");

    private BuildingAuditInputs() {}

    record Audit(
            int total, int done, Map<String, Integer> missing, Map<String, Integer> extra, int wrong, int unknown) {
        Audit {
            missing = Map.copyOf(Objects.requireNonNull(missing, "Недостача аудита не может быть null"));
            extra = Map.copyOf(Objects.requireNonNull(extra, "Лишнее аудита не может быть null"));
            if (total < 0 || done < 0 || done > total || wrong < 0 || unknown < 0) {
                throw new IllegalArgumentException("Аудит " + done + " из " + total + ", чужих " + wrong
                        + ", неизвестных " + unknown + ": числа не отрицательны, сделанное не больше плана");
            }
        }

        Audit plus(Audit other) {
            return new Audit(
                    total + other.total,
                    done + other.done,
                    merge(missing, other.missing),
                    merge(extra, other.extra),
                    wrong + other.wrong,
                    unknown + other.unknown);
        }

        int missingCount() {
            return missing.values().stream().mapToInt(Integer::intValue).sum();
        }

        int extraCount() {
            return extra.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    static Map<String, Integer> merge(Map<String, Integer> first, Map<String, Integer> second) {
        Map<String, Integer> out = new LinkedHashMap<>(first);
        second.forEach((block, count) -> out.merge(block, count, Integer::sum));
        return Map.copyOf(out);
    }

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
        town.ifPresent(plan -> placements(level, plan, scan, states, facts, now));
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
        RepairAudit.Report report = WorldAreaInputs.houseReport(level, scan.stage(), scan.wood(), now);
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

    static Map<String, List<Placement>> grouped(TownPlan plan) {
        Map<String, List<Placement>> groups = new LinkedHashMap<>();
        for (Placement place : plan.placements()) {
            if (!auditable(place.id())) {
                continue;
            }
            String key = key(place.id());
            if (STAGE_KEYS.contains(key)) {
                continue;
            }
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(place);
        }
        return groups;
    }

    private static void placements(
            ServerLevel level,
            TownPlan plan,
            FirstStepsRunner.StageScan scan,
            Map<String, Built> states,
            Map<String, GraphFacts.Building> facts,
            long now) {
        grouped(plan).forEach((key, places) -> {
            Placement first = places.get(0);
            GraphFacts.Position at =
                    new GraphFacts.Position(first.x(), scan.stage().anchor().y(), first.z());
            boolean built = false;
            List<Audit> audits = new ArrayList<>(places.size());
            for (Placement place : places) {
                built |= built(states, key, place.id());
                GraphFacts.Position spot =
                        new GraphFacts.Position(place.x(), scan.stage().anchor().y(), place.z());
                Optional<StageCatalog.Entry> entry = StageCatalog.entry(place.id());
                audits.add(entry.map(value -> BuildingAudit.scheme(level, value.scheme(), spot, scan.wood(), now))
                        .orElseGet(() -> BuildingAudit.stageOrchard(level, spot, scan.wood(), now)));
            }
            GraphFacts.Building base = facts.get(key);
            int currentLevel = built || (base != null && base.level() > 0) ? 1 : 0;
            facts.put(key, fact(key, currentLevel, at, sum(audits), built));
        });
    }

    static Audit sum(List<Audit> audits) {
        Audit out = new Audit(0, 0, Map.of(), Map.of(), 0, 0);
        for (Audit audit : audits) {
            out = out.plus(audit);
        }
        return out;
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
            return new GraphFacts.Building(id, level, 0, 0, Map.of(), Map.of(), 0, at, true, true);
        }
        int percent = audit.total() == 0 ? (built ? 100 : 0) : Math.min(100, audit.done() * 100 / audit.total());
        boolean started = built || audit.done() > 0;
        int damage = started ? Math.max(audit.missingCount() + audit.extraCount(), audit.total() - audit.done()) : 0;
        return new GraphFacts.Building(
                id, level, percent, damage, audit.missing(), audit.extra(), audit.wrong(), at, false, true);
    }
}
