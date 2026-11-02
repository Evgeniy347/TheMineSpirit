package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLogEntry;
import com.theminespirit.core.board.RepairAudit;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.repair.RepairData;
import com.theminespirit.core.town.FloatingBlocks;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownPlan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

final class WorldAreaInputs {
    private static final int PLACEMENT_SCAN = 256;
    private static final String PLACE = "place:";
    private static final String POS = "pos=";

    private static RepairAudit.@Nullable Report cachedHouse;
    private static @Nullable StageSite cachedHouseSite;
    private static long cachedHouseAt = Long.MIN_VALUE;

    record Placed(BlockCoord at, String type, @Nullable String builder) {
        Placed {
            if (at == null || type == null || type.isBlank()) {
                throw new IllegalArgumentException("След постройки без координаты или типа: нельзя отличить лишнее");
            }
        }
    }

    private WorldAreaInputs() {}

    static GraphFacts.World of(
            ServerLevel level,
            BoardService service,
            FirstStepsRunner.StageScan scan,
            Map<String, Built> states,
            Optional<TownPlan> town) {
        long now = service.board().now();
        StageSite site = scan.stage();
        List<GraphFacts.Stray> strays = strays(
                placements(service),
                at -> site.enclosure().contains(at.x(), at.z()),
                at -> site.planned(at.x(), at.y(), at.z()));
        PlotAudit.Audit audit = PlotAudit.audit(level, site, now);
        List<FloatingBlocks.Finding> floating = town.map(plan -> FloatingScanCycle.find(level, site, plan.region()))
                .orElse(List.of());
        GraphFacts.Terrain terrain =
                terrain(audit.holes(), audit.depths(), audit.extras(), floating.size(), strays.size());
        RepairAudit.Report house =
                service.board().ledger().has(BoardCase.BUILD_HOUSE, 1) ? houseReport(level, site, now) : null;
        Map<String, GraphFacts.Building> base = GraphFactInputs.buildingFacts(states, town, scan);
        return new GraphFacts.World(strays, terrain, buildings(base, house));
    }

    static List<GraphFacts.Stray> strays(
            List<Placed> placed, Predicate<BlockCoord> inside, Predicate<BlockCoord> planned) {
        Map<String, GraphFacts.Stray> found = new LinkedHashMap<>();
        for (Placed trace : placed) {
            if (!inside.test(trace.at()) || planned.test(trace.at())) {
                continue;
            }
            String key = key(trace.at());
            found.putIfAbsent(
                    key,
                    new GraphFacts.Stray(
                            "stray:" + trace.type() + "@" + key,
                            trace.type(),
                            new GraphFacts.Position(
                                    trace.at().x(), trace.at().y(), trace.at().z()),
                            trace.builder()));
        }
        return found.values().stream()
                .sorted(Comparator.comparingInt(
                                (GraphFacts.Stray stray) -> stray.position().y())
                        .thenComparingInt(stray -> stray.position().x())
                        .thenComparingInt(stray -> stray.position().z()))
                .toList();
    }

    static GraphFacts.Terrain terrain(
            List<BlockCoord> holes, Map<BlockCoord, Integer> depths, List<BlockCoord> extras, int hanging, int traces) {
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
        return new GraphFacts.Terrain(depths.size(), hanging, heightDrop, deepest, traces);
    }

    static Map<String, GraphFacts.Building> buildings(
            Map<String, GraphFacts.Building> base, RepairAudit.@Nullable Report house) {
        Map<String, GraphFacts.Building> facts = new LinkedHashMap<>();
        base.forEach((id, building) -> {
            boolean audited = house != null && "hut".equals(id);
            boolean unknown = audited ? house.unknown() > 0 : building.level() > 0 && building.auditPercent() == 100;
            int damage = audited && !unknown ? house.missing() : 0;
            int percent = audited && !unknown ? percent(house) : unknown ? 0 : building.auditPercent();
            facts.put(id, new GraphFacts.Building(id, building.level(), percent, damage, building.position(), unknown));
        });
        return facts;
    }

    private static RepairAudit.@Nullable Report houseReport(ServerLevel level, StageSite site, long now) {
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
