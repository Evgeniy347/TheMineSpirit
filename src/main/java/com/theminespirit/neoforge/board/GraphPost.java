package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.GatherBatch;
import com.theminespirit.core.board.MaterialSource;
import com.theminespirit.core.board.PlotLeveling;
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
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import org.jspecify.annotations.Nullable;

final class GraphPost {
    static final String MAKE = GraphTickets.PREFIX + "make:";
    private static final String PART = ":2";
    private static final Set<String> MINED = Set.of("cobblestone", "coal");
    private static final int TORCHES_PER_CRAFT = 4;
    private static final int TORCH_BATCH = 16;
    private static final int CHARCOAL_LOGS = 4;
    private static final int CHARCOAL_BATCH = 2;
    private static final String EAT = "eat";
    private static final Set<String> COLLECT = Set.of("collect_drops", "collect_saplings");

    private final ServerLevel level;
    private final BoardService service;
    private final List<SpiritEntity> spirits;
    private final StockView stock;
    private final FirstStepsRunner.StageScan scan;
    private final SupplyGraph graph;
    private final MaterialSource source;
    private final GraphSurroundings.Around around;
    private final long now;
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

    static List<String> ticketIds(GraphPlan.Task task, SupplyGraph graph) {
        String id = GraphTickets.id(task);
        if (task.id().startsWith("building:") && StageBuilt.ids().contains(task.makes())) {
            return FirstStepsRunner.ticketIds(
                    graph.buildings().get(task.makes()).boardCase());
        }
        return List.of(id, id + PART);
    }

    boolean post(GraphTickets.Ticket ticket) {
        GraphPlan.Task task = ticket.task();
        return switch (ticket.kind()) {
            case BUILDING -> StageBuilt.ids().contains(ticket.node()) ? building(ticket) : routeBuilding(ticket);
            case EVENT ->
                EAT.equals(ticket.node()) ? hungry(ticket) : COLLECT.contains(ticket.node()) && collect(ticket);
            case WAY -> way(ticket, task);
        };
    }

    private boolean hungry(GraphTickets.Ticket ticket) {
        GraphFood.post(
                level, service, spirits, stock, ticket.id(), ticket.task().priority(), now);
        return true;
    }

    private boolean collect(GraphTickets.Ticket ticket) {
        Optional<BlockCoord> at = "collect_drops".equals(ticket.node()) ? around.drop() : around.decay();
        if (at.isEmpty()) {
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
                TicketTarget.at(at.orElseThrow()));
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
            case GATHER_MATERIALS -> gather(ticket, task);
            case CRAFT_TORCHES -> torches(ticket, task);
            case SMELT_CHARCOAL -> charcoal(ticket, task);
            default -> false;
        };
    }

    private boolean building(GraphTickets.Ticket ticket) {
        GraphPlan.Task task = ticket.task();
        BoardCase boardCase = graph.buildings().get(ticket.node()).boardCase();
        if (FirstStepsRunner.ticketIds(boardCase).stream().anyMatch(this::open)) {
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
            if (!graph.items().containsKey(GraphNames.graphItem(item)) && known(item)) {
                gather(MAKE + item, ticket.id(), task.priority(), item, count, craftPoint());
            }
        });
        return true;
    }

    private boolean gather(GraphTickets.Ticket ticket, GraphPlan.Task task) {
        String item = GraphNames.item(graph, task.makes(), scan.wood());
        if (!graph.items().containsKey(task.makes()) || !known(item)) {
            return false;
        }
        Optional<RouteResource> route =
                graph.way(ticket.node()).map(SupplyGraph.Way::building).flatMap(RouteResource::ofBuilding);
        if (route.isPresent()) {
            return routeGather(ticket, item, route.orElseThrow());
        }
        gather(ticket.id(), ticket.id(), task.priority(), item, ticket.count(), source(ticket.node()));
        return true;
    }

    private boolean routeGather(GraphTickets.Ticket ticket, String item, RouteResource resource) {
        Optional<BlockCoord> point = RouteStore.get(level).point(resource);
        if (point.isEmpty()) {
            return false;
        }
        BlockCoord dig = point.orElseThrow();
        PlotLeveling plan = RouteWorks.dig(level, dig);
        if (plan.cut().isEmpty()) {
            return false;
        }
        BlockCoord home = scan.stage().workYard();
        int output = source.outputCount(item);
        int helpers = spirits.size();
        List<Integer> portions = GatherBatch.split(GatherBatch.round(ticket.count(), output), output, helpers);
        for (int part = 0; part < portions.size(); part++) {
            String partId = part == 0 ? ticket.id() : ticket.id() + PART;
            submit(
                    ticket(
                            partId,
                            ticket.task().boardCase(),
                            Optional.empty(),
                            ticket.task().priority(),
                            partId,
                            Map.of()),
                    TicketTarget.at(home)
                            .withRelocate(dig, home)
                            .withLeveling(plan)
                            .withSupply(item, portions.get(part)));
        }
        return true;
    }

    private BlockCoord source(String wayId) {
        Optional<SupplyGraph.Way> way = graph.way(wayId);
        Optional<BlockCoord> resource = way.map(SupplyGraph.Way::world).flatMap(around::resource);
        if (resource.isPresent()) {
            return GraphTargets.stand(level, resource.orElseThrow());
        }
        if (way.map(SupplyGraph.Way::building).filter("mine"::equals).isPresent()) {
            return GraphTargets.stand(level, scan.stage().mineEntrance());
        }
        return craftPoint();
    }

    private BlockCoord craftPoint() {
        if (craft == null) {
            boolean yard = scan.facts().done().contains(BoardCase.STOCK_CHESTS);
            craft = GraphTargets.craft(
                    level, yard ? scan.stage().workYard() : scan.stage().anchor());
        }
        return craft;
    }

    private void gather(String id, String cause, int priority, String item, int count, BlockCoord at) {
        int output = source.outputCount(item);
        int helpers = MINED.contains(item) ? miners() : spirits.size();
        List<Integer> portions = GatherBatch.split(GatherBatch.round(count, output), output, helpers);
        for (int part = 0; part < portions.size(); part++) {
            String partId = part == 0 ? id : id + PART;
            submit(
                    ticket(partId, BoardCase.GATHER_MATERIALS, Optional.empty(), priority, cause, Map.of()),
                    TicketTarget.at(at).withSupply(item, portions.get(part)));
        }
    }

    private boolean routeBuilding(GraphTickets.Ticket ticket) {
        Optional<RouteResource> resource = RouteResource.ofBuilding(ticket.node());
        if (resource.isEmpty()) {
            return false;
        }
        RouteResource known = resource.orElseThrow();
        RouteStore routes = RouteStore.get(level);
        Optional<BlockCoord> point = routes.point(known);
        if (point.isEmpty()) {
            return false;
        }
        BoardStatus status = service.board().statusOf(ticket.id()).orElse(null);
        if (status == BoardStatus.DONE) {
            if (known.isPath(ticket.node())) {
                routes.markPathBuilt(known);
            } else {
                routes.markRouteBuilt(known);
            }
            return true;
        }
        if (open(ticket.id())) {
            return true;
        }
        BlockCoord to = point.orElseThrow();
        TicketTarget target;
        if (known.isPath(ticket.node())) {
            PlotLeveling plan = RouteWorks.path(level, scan.stage().anchor(), to, pathBlock(ticket.node()));
            if (plan.fill().isEmpty()) {
                return false;
            }
            target = TicketTarget.at(to).withRelocate(scan.stage().anchor(), to).withLeveling(plan);
        } else if (known.isRoute(ticket.node())) {
            target = TicketTarget.at(to);
        } else {
            return false;
        }
        submit(
                ticket(
                        ticket.id(),
                        BoardCase.BUILD_STREETS,
                        Optional.empty(),
                        ticket.task().priority(),
                        ticket.id(),
                        Map.of()),
                target);
        return true;
    }

    private String pathBlock(String building) {
        SupplyGraph.Building known = graph.buildings().get(building);
        if (known == null || known.materials().isEmpty()) {
            return "dirt";
        }
        return known.materials().keySet().iterator().next();
    }

    private boolean tool(GraphTickets.Ticket ticket, GraphPlan.Task task) {
        UUID spirit = UUID.fromString(task.spirit().orElseThrow());
        submit(
                ticket(ticket.id(), task.boardCase(), Optional.of(spirit), task.priority(), ticket.id(), items(task)),
                TicketTarget.at(craftPoint()).withToolCount(1));
        return true;
    }

    private boolean torches(GraphTickets.Ticket ticket, GraphPlan.Task task) {
        int coal = stock.available().getOrDefault("coal", 0);
        int charcoal = stock.available().getOrDefault("charcoal", 0);
        String fuel = coal >= charcoal ? "coal" : "charcoal";
        int torches = Math.min(ticket.count(), TORCH_BATCH);
        int crafts = Math.min((torches + TORCHES_PER_CRAFT - 1) / TORCHES_PER_CRAFT, Math.max(coal, charcoal));
        if (crafts <= 0) {
            return true;
        }
        submit(
                ticket(
                        ticket.id(),
                        task.boardCase(),
                        Optional.empty(),
                        task.priority(),
                        ticket.id(),
                        Map.of(fuel, crafts)),
                TicketTarget.at(craftPoint()).withCount(crafts * TORCHES_PER_CRAFT));
        return true;
    }

    private boolean charcoal(GraphTickets.Ticket ticket, GraphPlan.Task task) {
        String log = GraphNames.item(graph, "#logs", scan.wood());
        Optional<UUID> holder = scan.furnaceBlock() ? Optional.empty() : furnaceHolder();
        submit(
                ticket(ticket.id(), task.boardCase(), holder, task.priority(), ticket.id(), Map.of(log, CHARCOAL_LOGS)),
                TicketTarget.at(craftPoint()).withSupply(log, CHARCOAL_LOGS).withCount(CHARCOAL_BATCH));
        return true;
    }

    private Map<String, Integer> items(GraphPlan.Task task) {
        Map<String, Integer> items = new HashMap<>();
        task.consumes()
                .forEach((item, count) -> items.merge(GraphNames.item(graph, item, scan.wood()), count, Integer::sum));
        return Map.copyOf(items);
    }

    private BoardTicket ticket(
            String id,
            BoardCase boardCase,
            Optional<UUID> assignee,
            int priority,
            String cause,
            Map<String, Integer> required) {
        return new BoardTicket(id, boardCase, assignee, priority, now, 1, cause, List.of(), required);
    }

    private void submit(BoardTicket ticket, TicketTarget target) {
        BoardOutcome outcome = service.submit(ticket, target);
        if (outcome == BoardOutcome.DUPLICATE) {
            service.board().reprioritize(ticket.id(), ticket.priority());
        }
    }

    private boolean open(String id) {
        return service.board()
                .statusOf(id)
                .filter(status -> switch (status) {
                    case PENDING, WAITING_RESOURCES, ASSIGNED -> true;
                    default -> false;
                })
                .isPresent();
    }

    private int miners() {
        int miners = 0;
        for (SpiritEntity spirit : spirits) {
            var inventory = spirit.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                if (inventory.getItem(slot).getItem() instanceof PickaxeItem) {
                    miners++;
                    break;
                }
            }
        }
        return miners;
    }

    private Optional<UUID> furnaceHolder() {
        return spirits.stream()
                .filter(spirit -> !spirit.isRemoved() && spirit.getInventory().countItem(Items.FURNACE) > 0)
                .map(SpiritEntity::getUUID)
                .findFirst();
    }

    private static boolean known(String item) {
        String name = item.startsWith("minecraft:") ? item.substring("minecraft:".length()) : item;
        return BuiltInRegistries.ITEM.containsKey(ResourceLocation.withDefaultNamespace(name));
    }
}
