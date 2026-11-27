package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BuildGap;
import com.theminespirit.core.board.CraterNeed;
import com.theminespirit.core.board.FillIndex;
import com.theminespirit.core.board.LevelRepair;
import com.theminespirit.core.board.RepairNeed;
import com.theminespirit.core.board.StageAudit;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.board.route.RouteRetired;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.api.CatalogLimits;
import com.theminespirit.core.town.PlotLeveling;
import com.theminespirit.core.town.SettlementScan;
import com.theminespirit.core.town.StageSite;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.slf4j.Logger;

final class WorldPost {
    static final String REPAIR_NODE = "repair:";
    static final String BACKFILL_PREFIX = "world:backfill:";
    static final List<String> OPEN_PREFIXES =
            List.of(BACKFILL_PREFIX, PlanBreachPost.PREFIX, AccessPost.PREFIX, AccessPost.CHEST_PREFIX);
    static final int BATCH = 8;
    private static final String REPAIR = REPAIR_NODE;
    private static final Set<BlockCoord> PLANNED_SKIPPED = ConcurrentHashMap.newKeySet();
    private static final Logger LOGGER = LogUtils.getLogger();

    private WorldPost() {}

    private static Predicate<BlockCoord> skipPlanned(StageSite site) {
        return at -> {
            if (!site.planCell(at.x(), at.y(), at.z()) && !streetLayer(site, at)) {
                return false;
            }
            if (PLANNED_SKIPPED.add(at)) {
                LOGGER.info("[plot] расчистка не трогает {}: там блок постройки или полоса улиц плана поселения", at);
            }
            return true;
        };
    }

    private static boolean streetLayer(StageSite site, BlockCoord at) {
        return site.streetColumn(at.x(), at.z())
                && at.y() >= site.anchor().y() - StageSite.STREET_DEPTH
                && at.y() <= site.anchor().y();
    }

    static boolean handles(String node) {
        return switch (node) {
            case "backfill", "level_lot", "plan_breach", "remove_route" -> true;
            default -> node.startsWith("repair:");
        };
    }

    static boolean post(GraphPost post, GraphTickets.Ticket ticket) {
        if (!ticket.node().startsWith(REPAIR)) {
            return world(post, ticket);
        }
        String id = ticket.node().substring(REPAIR.length());
        SupplyGraph.Building building = post.graph.buildings().get(id);
        if (building == null) {
            return false;
        }
        BoardCase step = building.boardCase();
        if (step == BoardCase.BUILD_ORCHARD) {
            return OrchardRepairPost.post(post, id);
        }
        String wood = BuildingWoodChoice.stored(post.level, step, post.scan.stage(), post.scan.wood());
        TicketTarget target = FirstStepsRunner.targetFor(post.level, step, post.scan.stage(), wood);
        if (step == BoardCase.BUILD_PLACED) {
            Optional<TicketTarget> placed = PlacedRepair.target(post, id, target);
            if (placed.isEmpty()) {
                return false;
            }
            target = placed.orElseThrow();
        }
        BuildGap.Gap gap = step == BoardCase.BUILD_PLACED
                ? PlacedRepair.gap(post, target, wood)
                : FirstStepsRunner.gap(post.level, step, post.scan.stage(), wood);
        if (!gap.confident()) {
            return false;
        }
        if (step == BoardCase.CLEAR_GROUNDS) {
            SettlementScan.Strays found = WorldAreaScan.cachedOrScan(post.level, post.scan.stage());
            List<BlockCoord> extras = WorldAreaScan.raised(found).stream()
                    .filter(ClearableCells.of(post.level))
                    .toList();
            List<BlockCoord> holes = WorldAreaScan.holes(found);
            if (extras.isEmpty() && holes.isEmpty()) {
                return false;
            }
            PlotLeveling leveling = LevelRepair.leveling(holes, extras, skipPlanned(post.scan.stage()));
            if (leveling.cut().isEmpty() && leveling.fill().isEmpty()) {
                return false;
            }
            target = target.withLeveling(leveling);
        }
        target = StageSettle.repairTarget(step, target, gap).withBuilding(RepairTitle.of(id, step));
        BoardOutcome outcome = StageRepair.post(
                post,
                step,
                target,
                new StageRepair.Gap(gap.shortage(), gap.total(), repairNeed(gap, ticket, post.graph, post.scan.wood())),
                step == BoardCase.BUILD_PLACED ? id : null);
        return StageAudit.keeps(outcome);
    }

    private static Map<String, Integer> repairNeed(
            BuildGap.Gap gap, GraphTickets.Ticket ticket, SupplyGraph graph, String wood) {
        return RepairNeed.merge(WorldNeeds.of(graph, ticket.task().consumes(), wood), gap.missingItems());
    }

    private static boolean world(GraphPost post, GraphTickets.Ticket ticket) {
        SettlementScan.Strays found = WorldAreaScan.last(post.level).orElse(SettlementScan.Strays.empty());
        int priority = ticket.task().priority();
        return switch (ticket.node()) {
            case "backfill" -> holes(post, ticket, found, priority);
            case "level_lot" -> MoundLots.post(post, ticket, found, priority, BATCH);
            case "plan_breach" -> PlanBreachPost.post(post, ticket);
            case "remove_route" -> removeRoute(post, ticket, priority);
            default -> false;
        };
    }

    private static boolean holes(
            GraphPost post, GraphTickets.Ticket ticket, SettlementScan.Strays found, int priority) {
        if (found.pits().isEmpty()) {
            return false;
        }
        int room = room(post.openCount(BACKFILL_PREFIX), BATCH);
        if (room == 0) {
            return true;
        }
        FillIndex fills = openFills(post);
        int posted = 0;
        for (Map.Entry<BlockCoord, Integer> pit : found.pits().entrySet()) {
            BlockCoord top = pit.getKey();
            int depth = pit.getValue();
            List<BlockCoord> cells = new ArrayList<>();
            for (int y = top.y() - depth + 1; y <= top.y(); y++) {
                cells.add(new BlockCoord(top.x(), y, top.z()));
            }
            String id = fills.covering(cells).orElse(null);
            if (id == null) {
                id = BACKFILL_PREFIX + top.x() + "," + top.y() + "," + top.z();
                if (post.done(id)) {
                    continue;
                }
            }
            if (posted >= room) {
                break;
            }
            if (post.open(id)) {
                continue;
            }
            TicketTarget target = TicketTarget.at(top)
                    .withCraterBlocks(Math.min(cells.size(), CatalogLimits.COUNT_MAX))
                    .withLeveling(LevelRepair.leveling(cells, List.of()));
            post.submit(
                    post.ticket(
                            id,
                            BoardCase.FILL_CRATER,
                            Optional.empty(),
                            priority,
                            ticket.id(),
                            CraterNeed.fillNeed(target)),
                    target);
            posted++;
        }
        return true;
    }

    private static FillIndex openFills(GraphPost post) {
        Map<String, PlotLeveling> fills = new LinkedHashMap<>();
        for (var ticket : post.service.board().tickets()) {
            if (!ticket.id().startsWith(BACKFILL_PREFIX)) {
                continue;
            }
            var status = post.service.board().statusOf(ticket.id()).orElse(null);
            if (status == null || status == BoardStatus.DONE || status == BoardStatus.CANCELLED) {
                continue;
            }
            PlotLeveling leveling = post.service
                    .targetOf(ticket.id())
                    .map(TicketTarget::leveling)
                    .orElse(null);
            if (leveling != null) {
                fills.put(ticket.id(), leveling);
            }
        }
        return FillIndex.of(fills);
    }

    private static boolean removeRoute(GraphPost post, GraphTickets.Ticket ticket, int priority) {
        RouteStore store = RouteStore.get(post.level);
        boolean posted = false;
        for (RouteRetired stale : store.retired()) {
            String id = "world:route_stale:" + stale.id();
            if (post.done(id)) {
                store.retireDone(stale.id());
                continue;
            }
            if (post.open(id)) {
                posted = true;
                continue;
            }
            BlockCoord first = stale.trail().first();
            post.submit(
                    post.ticket(id, BoardCase.REPAIR_BUILDING, Optional.empty(), priority, ticket.id(), Map.of()),
                    TicketTarget.at(first).withLeveling(stale.trail().removal()));
            posted = true;
        }
        return posted;
    }

    static int room(int open, int batch) {
        return Math.max(0, batch - open);
    }

    static String key(BlockCoord at) {
        return at.x() + "," + at.y() + "," + at.z();
    }

    static Map<String, Integer> need(SupplyGraph graph, Map<String, Integer> consumes, String wood) {
        return WorldNeeds.of(graph, consumes, wood);
    }
}
