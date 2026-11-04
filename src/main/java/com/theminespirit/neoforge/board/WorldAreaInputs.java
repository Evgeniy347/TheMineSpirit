package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardLogEntry;
import com.theminespirit.core.board.RepairAudit;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphSiteFacts;
import com.theminespirit.core.board.graph.GraphWorldFacts;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.repair.RepairData;
import com.theminespirit.core.town.FloatingBlocks;
import com.theminespirit.core.town.SettlementScan;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StrayGroups;
import com.theminespirit.core.town.TownPlan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

final class WorldAreaInputs {
    private static final int PLACEMENT_SCAN = 256;
    private static final String PLACE = "place:";
    private static final String POS = "pos=";

    private static RepairAudit.@Nullable Report cachedHouse;
    private static @Nullable StageSite cachedHouseSite;
    private static long cachedHouseAt = Long.MIN_VALUE;

    record Marks(Map<String, Integer> cut, Map<String, Integer> fill, Map<String, Integer> floating) {}

    record Placed(BlockCoord at, String type, @Nullable String builder) {
        Placed {
            if (at == null || type == null || type.isBlank()) {
                throw new IllegalArgumentException("След постройки без координаты или типа: нельзя отличить лишнее");
            }
        }
    }

    private WorldAreaInputs() {}

    static GraphWorldFacts of(
            ServerLevel level,
            BoardService service,
            FirstStepsRunner.StageScan scan,
            Optional<TownPlan> town,
            Map<String, GraphFacts.Building> buildings) {
        StageSite site = scan.stage();
        SettlementScan.Strays found = WorldAreaScan.scan(level, site, town);
        List<GraphSiteFacts.Stray> strays = new ArrayList<>(strays(found, placements(service)));
        Set<GraphFacts.Position> seen = new HashSet<>();
        strays.forEach(stray -> seen.add(stray.position()));
        town.ifPresent(plan -> FarmStray.facts(level, site, plan).stream()
                .filter(stray -> seen.add(stray.position()))
                .forEach(strays::add));
        List<FloatingBlocks.Finding> floating = town.map(plan -> FloatingScanCycle.find(level, site, plan.region()))
                .orElse(List.of());
        GraphSiteFacts.Terrain terrain = terrain(
                List.copyOf(found.pits().keySet()),
                found.pits(),
                found.mounds(),
                floating.size(),
                found.traces().size(),
                new Marks(
                        blockCounts(level, found.mounds()),
                        found.pits().isEmpty()
                                ? Map.of()
                                : Map.of("minecraft:dirt", found.pits().size()),
                        floatingCounts(floating)),
                found.unknown() > 0);
        return new GraphWorldFacts(strays, terrain, buildings);
    }

    static List<GraphSiteFacts.Stray> strays(SettlementScan.Strays found, List<Placed> placed) {
        Map<BlockCoord, String> builders = new HashMap<>();
        for (Placed trace : placed) {
            if (trace.builder() != null) {
                builders.putIfAbsent(trace.at(), trace.builder());
            }
        }
        List<GraphSiteFacts.Stray> facts = new ArrayList<>();
        for (StrayGroups.Group group : StrayGroups.of(found.strays(), builders)) {
            BlockCoord at = group.position();
            facts.add(new GraphSiteFacts.Stray(
                    "stray:" + group.type() + "@" + key(at),
                    group.type(),
                    new GraphFacts.Position(at.x(), at.y(), at.z()),
                    group.builder(),
                    group.blocks()));
        }
        return List.copyOf(facts);
    }

    static List<GraphSiteFacts.Stray> strays(
            List<Placed> placed, Predicate<BlockCoord> inside, Predicate<BlockCoord> planned) {
        Map<String, GraphSiteFacts.Stray> found = new LinkedHashMap<>();
        for (Placed trace : placed) {
            if (!inside.test(trace.at()) || planned.test(trace.at())) {
                continue;
            }
            String key = key(trace.at());
            found.putIfAbsent(
                    key,
                    new GraphSiteFacts.Stray(
                            "stray:" + trace.type() + "@" + key,
                            trace.type(),
                            new GraphFacts.Position(
                                    trace.at().x(), trace.at().y(), trace.at().z()),
                            trace.builder(),
                            Map.of(trace.type(), 1)));
        }
        return found.values().stream()
                .sorted(Comparator.comparingInt(
                                (GraphSiteFacts.Stray stray) -> stray.position().y())
                        .thenComparingInt(stray -> stray.position().x())
                        .thenComparingInt(stray -> stray.position().z()))
                .toList();
    }

    static GraphSiteFacts.Terrain terrain(
            List<BlockCoord> holes, Map<BlockCoord, Integer> depths, List<BlockCoord> extras, int hanging, int traces) {
        return terrain(holes, depths, extras, hanging, traces, new Marks(Map.of(), Map.of(), Map.of()));
    }

    static GraphSiteFacts.Terrain terrain(
            List<BlockCoord> holes,
            Map<BlockCoord, Integer> depths,
            List<BlockCoord> extras,
            int hanging,
            int traces,
            Marks marks) {
        return terrain(holes, depths, extras, hanging, traces, marks, false);
    }

    static GraphSiteFacts.Terrain terrain(
            List<BlockCoord> holes,
            Map<BlockCoord, Integer> depths,
            List<BlockCoord> extras,
            int hanging,
            int traces,
            Marks marks,
            boolean unknown) {
        if (hanging < 0 || traces < 0) {
            throw new IllegalArgumentException(
                    "Висящее " + hanging + " и следы " + traces + " не бывают отрицательными: отсутствие — ноль");
        }
        int deepest = depths.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int heightDrop = 0;
        if (!holes.isEmpty() && !extras.isEmpty()) {
            int top = extras.stream().mapToInt(BlockCoord::y).max().orElse(0);
            int bottom = holes.stream().mapToInt(BlockCoord::y).min().orElse(0);
            heightDrop = Math.max(0, top - bottom);
        }
        return new GraphSiteFacts.Terrain(
                depths.size(),
                hanging,
                heightDrop,
                deepest,
                traces,
                marks.cut(),
                marks.fill(),
                marks.floating(),
                unknown);
    }

    private static Map<String, Integer> blockCounts(ServerLevel level, List<BlockCoord> cells) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BlockCoord at : cells) {
            String block = BuiltInRegistries.BLOCK
                    .getKey(level.getBlockState(new BlockPos(at.x(), at.y(), at.z()))
                            .getBlock())
                    .toString();
            counts.merge(block, 1, Integer::sum);
        }
        return Map.copyOf(counts);
    }

    private static Map<String, Integer> floatingCounts(List<FloatingBlocks.Finding> findings) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (FloatingBlocks.Finding finding : findings) {
            counts.merge(finding.blockId(), finding.cells().size(), Integer::sum);
        }
        return Map.copyOf(counts);
    }

    static Map<String, GraphFacts.Building> buildings(
            Map<String, GraphFacts.Building> base, RepairAudit.@Nullable Report house) {
        Map<String, GraphFacts.Building> facts = new LinkedHashMap<>();
        base.forEach((id, building) -> {
            boolean audited = house != null && "hut".equals(id);
            boolean unknown = audited ? house.unknown() > 0 : building.level() > 0 && building.auditPercent() == 100;
            int damage = audited && !unknown ? house.missing() : 0;
            int percent = audited && !unknown ? percent(house) : unknown ? 0 : building.auditPercent();
            facts.put(
                    id,
                    new GraphFacts.Building(
                            id,
                            building.level(),
                            percent,
                            damage,
                            Map.of(),
                            Map.of(),
                            0,
                            building.position(),
                            unknown));
        });
        return facts;
    }

    static RepairAudit.@Nullable Report houseReport(ServerLevel level, StageSite site, long now) {
        long interval = RepairData.loadDefault().auditIntervalTicks();
        RepairAudit.Report cached = cachedHouse;
        if (cached != null && site.equals(cachedHouseSite) && now >= cachedHouseAt && now - cachedHouseAt < interval) {
            return cached;
        }
        RepairAudit.Report report = RepairAuditWatch.houseReport(level, site, now);
        if (report != null) {
            cachedHouse = report;
            cachedHouseSite = site;
            cachedHouseAt = now;
        }
        return report;
    }

    private static int percent(RepairAudit.Report report) {
        if (report.total() == 0) {
            return 100;
        }
        return Math.max(0, 100 - report.missing() * 100 / report.total());
    }

    private static List<Placed> placements(BoardService service) {
        List<Placed> placed = new ArrayList<>();
        for (BoardLogEntry entry : service.journal().last(PLACEMENT_SCAN)) {
            if (!entry.ticketId().startsWith(PLACE)) {
                continue;
            }
            BlockCoord at = position(entry.modifiers());
            if (at != null) {
                placed.add(new Placed(at, entry.ticketId().substring(PLACE.length()), entry.spirit()));
            }
        }
        return placed;
    }

    private static @Nullable BlockCoord position(List<String> details) {
        for (String detail : details) {
            if (!detail.startsWith(POS)) {
                continue;
            }
            String[] parts = detail.substring(POS.length()).split(",", -1);
            if (parts.length != 3) {
                return null;
            }
            try {
                return new BlockCoord(
                        Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim()),
                        Integer.parseInt(parts[2].trim()));
            } catch (NumberFormatException error) {
                return null;
            }
        }
        return null;
    }

    private static String key(BlockCoord at) {
        return at.x() + "," + at.y() + "," + at.z();
    }
}
