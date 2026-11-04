package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.GatherBatch;
import com.theminespirit.core.board.MaterialSource;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.ToolSet;
import com.theminespirit.core.board.graph.ActivityPlans;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphNames;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.board.graph.StageBuilt;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.board.route.RouteResource;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.planner.CraftedItems;
import com.theminespirit.neoforge.SpiritEntity;
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
    private static final Set<BoardStatus> OPEN_STATUSES =
            Set.of(BoardStatus.PENDING, BoardStatus.WAITING_RESOURCES, BoardStatus.ASSIGNED);

    final ServerLevel level;
    final FirstStepsRunner.StageScan scan;
    final GraphSurroundings.Around around;
    final StockView stock;
    final long now;
    final List<SpiritEntity> spirits;
    final SupplyGraph graph;
    final BoardService service;
    final MaterialSource source;
    final PetWork pets;
    final GraphState state;
    final GraphFacts facts;
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
        this.pets = site.pets();
        this.state = site.state();
        this.service = service;
        this.spirits = spirits;
        this.stock = stock;
        this.scan = site.scan();
        this.graph = graph;
        this.source = FirstStepsRunner.materialSource(level);
        this.around = site.around();
        this.now = now;
        this.facts = site.state().facts();
    }

    record Site(FirstStepsRunner.StageScan scan, GraphSurroundings.Around around, PetWork pets, GraphState state) {}

    boolean post(GraphTickets.Ticket ticket) {
        GraphPlan.Task task = ticket.task();
        if (MoreAreaPost.handles(ticket.node())) {
            return MoreAreaPost.post(this, ticket);
        }
        return switch (ticket.kind()) {
            case BUILDING ->
                MorePost.building(this, ticket)
                        || FarmPost.building(this, ticket)
                        || GraphNodeBuilds.post(this, ticket, task)
                        || PortalPost.building(this, ticket)
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
        return GraphEventPost.post(this, ticket);
    }

    private boolean way(GraphTickets.Ticket ticket, GraphPlan.Task task) {
        if (GraphMorePost.handles(ticket)) return GraphMorePost.post(this, ticket, task);
        BoardCase boardCase = task.boardCase();
        if (ToolSet.isTool(boardCase) && task.spirit().isPresent()) {
            return tool(ticket, task);
        }
        if (task.spirit().isPresent()) {
            return new GraphCrafting(this).spiritCraft(ticket, task);
        }
        if (StorePost.way(this, ticket)) {
            return true;
        }
        return switch (boardCase) {
            case REPAIR_TOOL -> RepairWatch.post(this, task.priority());
            case RESTOCK_FOOD -> {
                GraphFood.post(this, ticket.id(), task.priority());
                yield true;
            }
            case CRAFTING_STATION -> new GraphCrafting(this).station(ticket, task);
            case GATHER_MATERIALS ->
                FarmPost.pet(this, ticket) || PortalPost.way(this, ticket, task) || gather(ticket, task);
            case COLLECT_DROPS -> GraphCollect.post(this, around, ticket);
            case HARVEST_ORCHARD -> new GraphHarvestPost(this).orchard(ticket, task);
            case MINE_LEFT_ORE -> new GraphHarvestPost(this).leftOre(ticket, task);
            case CRAFT_TORCHES -> new GraphCrafting(this).torches(ticket, task);
            case STOCK_CHESTS -> ChestSpaceWatch.post(this);
            case SMELT_CHARCOAL -> new GraphCrafting(this).charcoal(ticket, task);
            case GRIND_BLAZE_POWDER, BREW_POTION, HARVEST_NETHER_WART -> GraphBrew.post(this, ticket, task);
            case ACTIVITY_MOVE -> DigOutPost.digOut(this, service, task);
            case RESCUE_SPIRIT -> GraphRescuePost.rescue(this, ticket, task);
            case FILL_CRATER -> new GraphHarvestPost(this).craters(ticket);
            default -> MorePost.way(this, ticket, task) || FarmPost.way(this, ticket, task);
        };
    }

    private boolean building(GraphTickets.Ticket ticket) {
        GraphPlan.Task task = ticket.task();
        BoardCase boardCase = graph.buildings().get(ticket.node()).boardCase();
        if (StageOpen.full(service.board(), spirits.size())) {
            return true;
        }
        Map<String, Integer> missing =
                FirstStepsRunner.open(this, new FirstStepsRunner.StepOrder(boardCase, task.priority(), ticket.id()));
        missing.forEach((item, count) -> {
            if (GraphItemIds.known(item) && !CraftedItems.isCrafted(item)) {
                gather(MAKE + item, ticket.id(), task.priority(), item, count, craftPoint());
            }
        });
        return true;
    }

    private boolean gather(GraphTickets.Ticket ticket, GraphPlan.Task task) {
        String item = GraphNames.item(graph, task.makes(), scan.wood());
        if ("fuel_planks".equals(task.makes())) {
            item = scan.wood() + "_planks";
        }
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

    BoardTicket leveled(BoardTicket base, int level, List<String> dependsOn) {
        return new BoardTicket(
                base.id(),
                base.boardCase(),
                base.assignee(),
                base.priority(),
                base.createdAt(),
                level,
                base.cause(),
                dependsOn,
                base.requiredItems());
    }

    BoardTicket dated(BoardTicket base, long createdAt) {
        return new BoardTicket(
                base.id(),
                base.boardCase(),
                base.assignee(),
                base.priority(),
                createdAt,
                base.level(),
                base.cause(),
                base.dependsOn(),
                base.requiredItems());
    }

    BoardOutcome submit(BoardTicket ticket, TicketTarget target) {
        BoardOutcome outcome = service.submit(ticket, target);
        if (outcome == BoardOutcome.DUPLICATE) service.board().reprioritize(ticket.id(), ticket.priority());
        return outcome;
    }

    boolean done(String id) {
        return service.board().statusOf(id).filter(BoardStatus.DONE::equals).isPresent();
    }

    boolean open(String id) {
        return service.board().statusOf(id).filter(OPEN_STATUSES::contains).isPresent();
    }

    boolean launchActivity(SpiritEntity spirit, BoardCase activity, ActivityPlans.Plan plan) {
        return service.launch(
                spirit,
                activity,
                plan.script(),
                plan.task(),
                (member, name, task, tick) -> {
                    if (member.isRemoved()) return false;
                    JobLaunch.start(
                            service.board(), service.cards, member, BoardActivities.idOf(member.getUUID()), task);
                    return true;
                },
                now);
    }

    private GraphRoutes routes() {
        return new GraphRoutes(this, level, service, scan, graph, source, spirits.size());
    }
}
