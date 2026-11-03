package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.GatherBatch;
import com.theminespirit.core.board.MaterialSource;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.ToolSet;
import com.theminespirit.core.board.graph.GraphNames;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.board.graph.StageBuilt;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.board.route.RouteResource;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.planner.CraftedItems;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.terminal.CraftOrders;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

final class GraphPost {
    static final String MAKE = GraphTickets.PREFIX + "make:";
    static final String PART = ":2";
    private static final Set<String> MINED = Set.of("cobblestone", "coal");
    private static final String EAT = "eat";
    private static final String ORDER = "craft_player_order";
    private static final Set<String> DEMOLITION = Set.of("demolish_stray", "clear_floating");
    private static final Set<String> COLLECT = Set.of("collect_drops", "collect_saplings");

    final ServerLevel level;
    final FirstStepsRunner.StageScan scan;
    final GraphSurroundings.Around around;
    final StockView stock;
    final long now;
    final List<SpiritEntity> spirits;
    final SupplyGraph graph;
    private final BoardService service;
    private final MaterialSource source;
    private @Nullable BlockCoord craft;

    GraphPost(
            ServerLevel level,
            BoardService service,
            List<SpiritEntity> spirits,
            StockView stock,
            Site site,
            SupplyGraph graph,
            long now) {
        this.level = level;
        this.service = service;
        this.spirits = spirits;
        this.stock = stock;
        this.scan = site.scan();
        this.graph = graph;
        this.source = FirstStepsRunner.materialSource(level);
        this.around = site.around();
        this.now = now;
    }

    record Site(FirstStepsRunner.StageScan scan, GraphSurroundings.Around around) {}

    boolean post(GraphTickets.Ticket ticket) {
        GraphPlan.Task task = ticket.task();
        return switch (ticket.kind()) {
            case BUILDING ->
                PortalPost.building(this, ticket)
                        || (ExtraOrchardPost.handles(ticket.node())
                                ? ExtraOrchardPost.post(this, ticket, task)
                                : StagePlacePost.handles(ticket.node())
                                        ? StagePlacePost.post(this, ticket)
                                        : StageBuilt.ids().contains(ticket.node())
                                                ? building(ticket)
                                                : routes().building(ticket));
            case EVENT -> event(ticket);
            case WAY -> way(ticket, task);
        };
    }

    private boolean event(GraphTickets.Ticket ticket) {
        if (life(ticket)) {
            return true;
        }
        if (EAT.equals(ticket.node())) {
            return hungry(ticket);
        }
        if (ORDER.equals(ticket.node())) {
            CraftOrders.refresh(level);
            return true;
        }
        if (DEMOLITION.contains(ticket.node())) {
            return demolition();
        }
        if (WorldPost.handles(ticket.node())) {
            return WorldPost.post();
        }
        return COLLECT.contains(ticket.node()) && collect(ticket);
    }

    private boolean life(GraphTickets.Ticket ticket) {
        return ticket.id().startsWith(GraphTickets.PREFIX + "event:life_");
    }

    private boolean demolition() {
        Optional<TownPlan> town = SettlementPlotStore.get(level).town();
        if (town.isEmpty()) {
            return false;
        }
        PlanDemolition.tick(level, service, scan.stage(), town.orElseThrow(), now, this);
        return true;
    }

    private boolean hungry(GraphTickets.Ticket ticket) {
        GraphFood.post(
                level, service, spirits, stock, ticket.id(), ticket.task().priority(), now);
        return true;
    }

    private boolean collect(GraphTickets.Ticket ticket) {
        boolean saplings = "collect_saplings".equals(ticket.node());
        Optional<BlockCoord> at = saplings ? around.decay() : around.anyDrop();
        if (at.isEmpty()) {
            return false;
        }
        Optional<BlockCoord> target = saplings ? GraphTargets.leafDropStand(level, at.orElseThrow()) : at;
        if (target.isEmpty()) {
            return false;
        }
        submit(
                ticket(
                        ticket.id(),
                        BoardCase.COLLECT_DROPS,
                        Optional.empty(),
                        ticket.task().priority(),
                        ticket.id(),
                        Map.of()),
                TicketTarget.at(target.orElseThrow()));
        return true;
    }

    private boolean way(GraphTickets.Ticket ticket, GraphPlan.Task task) {
        BoardCase boardCase = task.boardCase();
        if (ToolSet.isTool(boardCase) && task.spirit().isPresent()) {
            return tool(ticket, task);
        }
        if (task.spirit().isPresent()) {
            return false;
        }
        return switch (boardCase) {
            case GATHER_MATERIALS -> PortalPost.way(this, ticket, task) || gather(ticket, task);
            case COLLECT_DROPS -> GraphCollect.post(service, around, ticket, now);
            case CRAFT_TORCHES -> new GraphCrafting(this).torches(ticket, task);
            case SMELT_CHARCOAL -> new GraphCrafting(this).charcoal(ticket, task);
            case GRIND_BLAZE_POWDER, BREW_POTION, HARVEST_NETHER_WART -> GraphBrew.post(this, ticket, task);
            case ACTIVITY_MOVE -> DigOutPost.digOut(this, service, task);
            default -> false;
        };
    }

    private boolean building(GraphTickets.Ticket ticket) {
        GraphPlan.Task task = ticket.task();
        BoardCase boardCase = graph.buildings().get(ticket.node()).boardCase();
        if (FirstStepsRunner.ticketIds(boardCase).stream().anyMatch(this::open)) {
            StageSettle.refresh(level, service, stock, scan, boardCase, now);
            return true;
        }
        if (StageSettle.settle(level, service, stock, scan, boardCase, now)) {
            return true;
        }
        if (StageOpen.full(service.board(), spirits.size())) {
            return true;
        }
        Map<String, Integer> missing = FirstStepsRunner.open(
                level,
                service,
                stock,
                scan,
                new FirstStepsRunner.StepOrder(boardCase, task.priority(), ticket.id()),
                now);
        missing.forEach((item, count) -> {
            if (GraphItemIds.known(item) && !CraftedItems.isCrafted(item)) {
                gather(MAKE + item, ticket.id(), task.priority(), item, count, craftPoint());
            }
        });
        return true;
    }

    private boolean gather(GraphTickets.Ticket ticket, GraphPlan.Task task) {
        String item = GraphNames.item(graph, task.makes(), scan.wood());
        if (!graph.items().containsKey(task.makes()) || !GraphItemIds.known(item)) {
            return false;
        }
        Optional<RouteResource> route =
                graph.way(ticket.node()).map(SupplyGraph.Way::building).flatMap(RouteResource::ofBuilding);
        if (route.isPresent()) {
            return routes().gather(ticket, item, route.orElseThrow());
        }
        gather(ticket.id(), ticket.id(), task.priority(), item, ticket.count(), source(ticket.node()));
        return true;
    }

    BlockCoord source(String wayId) {
        return GraphTargets.source(level, graph.way(wayId), around, scan, this::craftPoint);
    }

    BlockCoord craftPoint() {
        if (craft == null) {
            boolean yard = scan.facts().done().contains(BoardCase.STOCK_CHESTS);
            craft = GraphTargets.craft(
                    level, yard ? scan.stage().workYard() : scan.stage().anchor());
        }
        return craft;
    }

    void gather(String id, String cause, int priority, String item, int count, BlockCoord at) {
        int output = source.outputCount(item);
        int helpers = MINED.contains(item) ? GraphCrew.miners(spirits) : spirits.size();
        List<Integer> portions = GatherBatch.split(GatherBatch.round(count, output), output, helpers);
        for (int part = 0; part < portions.size(); part++) {
            String partId = part == 0 ? id : id + PART;
            submit(
                    ticket(partId, BoardCase.GATHER_MATERIALS, Optional.empty(), priority, cause, Map.of()),
                    TicketTarget.at(at).withSupply(item, portions.get(part)));
        }
    }

    private boolean tool(GraphTickets.Ticket ticket, GraphPlan.Task task) {
        UUID spirit = UUID.fromString(task.spirit().orElseThrow());
        submit(
                ticket(ticket.id(), task.boardCase(), Optional.of(spirit), task.priority(), ticket.id(), items(task)),
                TicketTarget.at(craftPoint()).withToolCount(1));
        return true;
    }

    Map<String, Integer> items(GraphPlan.Task task) {
        Map<String, Integer> items = new HashMap<>();
        task.consumes()
                .forEach((item, count) -> items.merge(GraphNames.item(graph, item, scan.wood()), count, Integer::sum));
        return Map.copyOf(items);
    }

    BoardTicket ticket(
            String id,
            BoardCase boardCase,
            Optional<UUID> assignee,
            int priority,
            String cause,
            Map<String, Integer> required) {
        return new BoardTicket(id, boardCase, assignee, priority, now, 1, cause, List.of(), required);
    }

    BoardOutcome submit(BoardTicket ticket, TicketTarget target) {
        BoardOutcome outcome = service.submit(ticket, target);
        if (outcome == BoardOutcome.DUPLICATE) {
            service.board().reprioritize(ticket.id(), ticket.priority());
        }
        return outcome;
    }

    boolean done(String id) {
        return service.board().statusOf(id).filter(BoardStatus.DONE::equals).isPresent();
    }

    boolean open(String id) {
        return service.board()
                .statusOf(id)
                .filter(status -> switch (status) {
                    case PENDING, WAITING_RESOURCES, ASSIGNED -> true;
                    default -> false;
                })
                .isPresent();
    }

    private GraphRoutes routes() {
        return new GraphRoutes(this, level, service, scan, graph, source, spirits.size());
    }
}
