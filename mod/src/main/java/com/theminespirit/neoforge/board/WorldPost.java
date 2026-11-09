package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.BuildGap;
import com.theminespirit.core.board.FillIndex;
import com.theminespirit.core.board.LevelRepair;
import com.theminespirit.core.board.StageAudit;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphNames;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.town.PitGroups;
import com.theminespirit.core.town.PlotLeveling;
import com.theminespirit.core.town.SettlementScan;
import com.theminespirit.neoforge.policy.PlanBreachData;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;

final class WorldPost {
    static final String REPAIR_NODE = "repair:";
    private static final String REPAIR = REPAIR_NODE;
    private static final Comparator<BlockCoord> ORDER = Comparator.comparingInt(BlockCoord::y)
            .thenComparingInt(BlockCoord::x)
            .thenComparingInt(BlockCoord::z);
    private static final int BATCH = 8;

    private WorldPost() {}

    static boolean handles(String node) {
        return switch (node) {
            case "backfill", "level_lot", "restore_trace" -> true;
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
            SettlementScan.Strays found = WorldAreaScan.last(post.level)
                    .orElseGet(() -> WorldAreaScan.scan(
                            post.level,
                            post.scan.stage(),
                            SettlementPlotStore.get(post.level).town()));
            List<BlockCoord> extras = WorldAreaScan.raised(found);
            List<BlockCoord> holes = WorldAreaScan.holes(found);
            if (extras.isEmpty() && holes.isEmpty()) {
                return false;
            }
            target = target.withLeveling(LevelRepair.leveling(holes, extras));
        }
        target = StageSettle.repairTarget(step, target, gap);
        BoardOutcome outcome = StageRepair.post(
                post,
                step,
                target,
                new StageRepair.Gap(
                        gap.shortage(),
                        gap.total(),
                        need(post.graph, ticket.task().consumes(), post.scan.wood())),
                step == BoardCase.BUILD_PLACED ? id : null);
        return StageAudit.keeps(outcome);
    }

    private static boolean world(GraphPost post, GraphTickets.Ticket ticket) {
        SettlementScan.Strays found = WorldAreaScan.last(post.level).orElse(SettlementScan.Strays.empty());
        int priority = ticket.task().priority();
        return switch (ticket.node()) {
            case "backfill" -> holes(post, ticket, found, priority);
            case "level_lot" -> lot(post, ticket, found, priority);
            case "restore_trace" -> {
                boolean plan = breaches(post);
                boolean trace = traces(post, ticket, found, priority);
                yield plan || trace;
            }
            default -> false;
        };
    }

    private static boolean holes(
            GraphPost post, GraphTickets.Ticket ticket, SettlementScan.Strays found, int priority) {
        if (found.pits().isEmpty()) {
            return false;
        }
        int room = room(open(post, "world:backfill:"), BATCH);
        if (room == 0) {
            return true;
        }
        FillIndex fills = openFills(post);
        int posted = 0;
        for (PitGroups.Pit pit : PitGroups.group(found.pits())) {
            String id = fills.covering(pit.cells()).orElse(null);
            if (id == null) {
                id = "world:backfill:" + pit.key();
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
            post.submit(
                    post.ticket(
                            id,
                            BoardCase.FILL_CRATER,
                            Optional.empty(),
                            priority,
                            ticket.id(),
                            post.items(ticket.task())),
                    TicketTarget.at(pit.anchor())
                            .withCraterBlocks(Math.min(pit.blocks(), CatalogLimits.COUNT_MAX))
                            .withLeveling(LevelRepair.leveling(pit.cells(), List.of())));
            posted++;
        }
        return true;
    }

    private static FillIndex openFills(GraphPost post) {
        Map<String, PlotLeveling> fills = new LinkedHashMap<>();
        for (var ticket : post.service.board().all()) {
            if (!ticket.id().startsWith("world:backfill:")) {
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

    private static boolean lot(GraphPost post, GraphTickets.Ticket ticket, SettlementScan.Strays found, int priority) {
        if (found.mounds().isEmpty()) {
            return false;
        }
        List<BlockCoord> extras =
                found.mounds().stream().sorted(ORDER).limit(BATCH).toList();
        BlockCoord first = extras.get(0);
        String id = "world:level_lot:" + key(first);
        if (post.done(id) || post.open(id)) {
            return true;
        }
        post.submit(
                post.ticket(
                        id,
                        BoardCase.REPAIR_BUILDING,
                        Optional.empty(),
                        priority,
                        ticket.id(),
                        post.items(ticket.task())),
                TicketTarget.at(first).withLeveling(LevelRepair.leveling(List.of(), extras)));
        return true;
    }

    private static boolean breaches(GraphPost post) {
        PlanBreachData data = PlanBreachData.get(post.level);
        boolean posted = false;
        for (Map.Entry<BlockPos, PlanBreachData.Breach> entry : data.open().entrySet()) {
            BlockPos pos = entry.getKey();
            if (!post.level.getBlockState(pos).isAir()) {
                data.clear(pos);
                continue;
            }
            String id = "world:plan_breach:" + key(new BlockCoord(pos.getX(), pos.getY(), pos.getZ())) + ":"
                    + entry.getValue().tick();
            if (post.open(id)) {
                posted = true;
                continue;
            }
            post.submit(
                    post.ticket(
                            id,
                            BoardCase.REPAIR_BUILDING,
                            Optional.empty(),
                            BoardTuning.MAX_PRIORITY,
                            BoardTicket.ALWAYS_CAUSE,
                            Map.of()),
                    TicketTarget.at(new BlockCoord(pos.getX(), pos.getY(), pos.getZ()))
                            .withPlan(new ScriptCall(
                                    "place_block",
                                    Map.of(
                                            "block", entry.getValue().blockId(),
                                            "x", pos.getX(),
                                            "y", pos.getY(),
                                            "z", pos.getZ()))));
            posted = true;
        }
        return posted;
    }

    private static boolean traces(
            GraphPost post, GraphTickets.Ticket ticket, SettlementScan.Strays found, int priority) {
        if (found.traces().isEmpty()) {
            return false;
        }
        int room = room(open(post, "world:trace:"), BATCH);
        if (room == 0) {
            return true;
        }
        boolean posted = false;
        for (BlockCoord at : found.traces().stream().sorted(ORDER).limit(room).toList()) {
            String id = "world:trace:" + key(at);
            if (post.done(id) || post.open(id)) {
                posted = true;
                continue;
            }
            post.submit(
                    post.ticket(
                            id,
                            BoardCase.FILL_CRATER,
                            Optional.empty(),
                            priority,
                            ticket.id(),
                            post.items(ticket.task())),
                    TicketTarget.at(at).withCraterBlocks(1));
            posted = true;
        }
        return posted;
    }

    static int room(int open, int batch) {
        return Math.max(0, batch - open);
    }

    private static int open(GraphPost post, String prefix) {
        int open = 0;
        for (var ticket : post.service.board().all()) {
            if (!ticket.id().startsWith(prefix)) {
                continue;
            }
            var status = post.service.board().statusOf(ticket.id()).orElse(null);
            if (status == BoardStatus.PENDING
                    || status == BoardStatus.WAITING_RESOURCES
                    || status == BoardStatus.ASSIGNED) {
                open++;
            }
        }
        return open;
    }

    private static String key(BlockCoord at) {
        return at.x() + "," + at.y() + "," + at.z();
    }

    static Map<String, Integer> need(SupplyGraph graph, Map<String, Integer> consumes, String wood) {
        Map<String, Integer> need = new LinkedHashMap<>();
        consumes.forEach((item, count) -> {
            String named = GraphNames.item(graph, item, wood);
            need.merge(
                    named.startsWith("minecraft:") ? named.substring("minecraft:".length()) : named,
                    count,
                    Integer::sum);
        });
        return Map.copyOf(need);
    }
}
