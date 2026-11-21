package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardLedger;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.ChestClearance;
import com.theminespirit.core.board.ChestPlans;
import com.theminespirit.core.board.StateChangeLog;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.SettlementChestStore;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import com.theminespirit.neoforge.policy.PolicyConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.slf4j.Logger;

public final class ChestDemolition {
    static final String EVENT = "demolish_chest";
    private static final String TICKET = "demolish_chest:";
    private static final String CHEST = "warehouse_chest:";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final StateChangeLog CHANGES = new StateChangeLog();

    private ChestDemolition() {}

    public static boolean removable(ServerLevel level, BlockCoord at) {
        BlockPos pos = new BlockPos(at.x(), at.y(), at.z());
        if (!(ContainerAccess.inspect(level, pos) instanceof ContainerAccess.Opened)) {
            return false;
        }
        BoardLedger ledger = BoardRegistry.get(level).board().ledger();
        var site = SettlementPlotStore.liveSite(level);
        for (BlockCoord cell : chestCells(level, at)) {
            BlockPos block = new BlockPos(cell.x(), cell.y(), cell.z());
            boolean marked = PlacedBlocksData.get(level).isMarked(block)
                    && site.map(place -> WarehouseLocations.onSettlementFloor(level, place, cell))
                            .orElse(true);
            if (WarehouseLocations.planned(level, ledger, cell)
                    || marked
                    || PolicyConfig.zones()
                            .contains(level.dimension().location().toString(), cell)) {
                return false;
            }
        }
        return true;
    }

    static boolean pending(ServerLevel level, BoardService service) {
        BoardLedger ledger = service.board().ledger();
        if (ChestSnapshots.area(level).isEmpty()) {
            return false;
        }
        List<BlockCoord> extras = WarehouseLocations.extras(level, ledger);
        if (extras.isEmpty()) {
            return false;
        }
        SettlementChestStore store = SettlementChestStore.get(level);
        for (BlockCoord at : extras) {
            store.forget(new BlockPos(at.x(), at.y(), at.z()));
        }
        return true;
    }

    static boolean tick(GraphPost post, GraphTickets.Ticket ticket) {
        if (!EVENT.equals(ticket.node())) {
            return false;
        }
        BoardLedger ledger = post.service.board().ledger();
        Set<BlockCoord> cells = WarehouseLocations.storeCells(post.level, ledger);
        if (cells.isEmpty() && SettlementPlotStore.liveSite(post.level).isEmpty()) {
            return true;
        }
        if (busy(post, TICKET)) {
            return true;
        }
        List<BlockCoord> extras = new ArrayList<>(WarehouseLocations.extras(post.level, ledger));
        extras.sort(
                (first, second) -> Long.compare(distance(post.scan.home(), first), distance(post.scan.home(), second)));
        List<ContainerSnapshot> warehouse = ChestSnapshots.read(post.level, List.copyOf(cells));
        List<BlockCoord> emptyCells = emptyCells(post.level, cells);
        int planks = planks(post);
        boolean expand = false;
        for (BlockCoord at : extras) {
            Optional<ContainerSnapshot> snapshot = Optional.ofNullable(ChestSnapshots.forDemolition(post.level, at));
            if (snapshot.isEmpty()) {
                continue;
            }
            ContainerSnapshot chest = snapshot.orElseThrow();
            ChestClearance.Decision decision = ChestClearance.decide(chest, false, warehouse, emptyCells, planks);
            switch (decision.action()) {
                case KEEP -> {
                    continue;
                }
                case REMOVE -> {
                    if (submit(post, ticket, at, ChestPlans.breakOnly(chestCells(post.level, at)), chest, decision)) {
                        return true;
                    }
                }
                case DRAIN -> {
                    List<BlockCoord> occupied = chestCells(post.level, at);
                    TaskNode plan = ChestPlans.demolish(chest, occupied, null, covers(post, occupied));
                    if (submit(post, ticket, at, plan, chest, decision)) {
                        return true;
                    }
                }
                case ADD_CHEST -> expand = true;
                case WAIT -> {
                    if (CHANGES.changed("chest-wait:" + at, decision.reason())) {
                        LOGGER.info("[warehouse] сундук {} ждёт: {}", at, decision.reason());
                    }
                }
            }
        }
        if (expand && !busy(post, CHEST) && !emptyCells.isEmpty()) {
            placeChest(post, ticket, emptyCells.get(0));
        }
        return true;
    }

    private static boolean submit(
            GraphPost post,
            GraphTickets.Ticket ticket,
            BlockCoord at,
            TaskNode plan,
            ContainerSnapshot chest,
            ChestClearance.Decision decision) {
        StageWork work = new StageWork(
                post.scan.stage(),
                chestCells(post.level, at).stream()
                        .map(cell -> new PlannedBlock(cell, "minecraft:air"))
                        .toList());
        String id = TICKET + at.x() + "," + at.y() + "," + at.z();
        BoardOutcome outcome = post.submit(
                post.ticket(
                        id,
                        BoardCase.DISMANTLE_SHELTER,
                        Optional.empty(),
                        ticket.task().priority(),
                        ticket.id(),
                        Map.of()),
                TicketTarget.at(at).withPlan(plan).withWork(work));
        if (outcome != BoardOutcome.ACCEPTED) {
            return false;
        }
        LOGGER.info(
                "[warehouse] сундук {}: {} (вещей {}, слотов нужно {} из {})",
                at,
                ContainerAccess.open(post.level, new BlockPos(at.x(), at.y(), at.z())) instanceof ContainerAccess.Opened
                        ? decision.reason()
                        : "крышка закрыта — сломать и сразу подобрать содержимое с разгрузкой на склад",
                ChestClearance.moves(chest).size(),
                decision.neededSlots(),
                decision.freeSlots());
        return true;
    }

    private static void placeChest(GraphPost post, GraphTickets.Ticket ticket, BlockCoord cell) {
        TaskNode plan = ChestPlans.placeOne(cell);
        String id = CHEST + cell.x() + "," + cell.y() + "," + cell.z();
        post.submit(
                post.ticket(
                        id,
                        BoardCase.STOCK_CHESTS,
                        Optional.empty(),
                        BoardRules.priorityOf(BoardCase.STOCK_CHESTS),
                        ticket.id(),
                        Map.of()),
                TicketTarget.at(cell).withPlan(plan));
        LOGGER.info("[warehouse] склад полон: новый сундук ставится в клетку склада {}", cell);
    }

    private static boolean busy(GraphPost post, String prefix) {
        return post.service.board().tickets().stream()
                .filter(ticket -> ticket.id().startsWith(prefix))
                .anyMatch(ticket -> {
                    BoardStatus status =
                            post.service.board().statusOf(ticket.id()).orElse(BoardStatus.DONE);
                    return status == BoardStatus.PENDING
                            || status == BoardStatus.WAITING_RESOURCES
                            || status == BoardStatus.ASSIGNED;
                });
    }

    private static List<BlockCoord> chestCells(ServerLevel level, BlockCoord at) {
        List<BlockCoord> cells = new ArrayList<>(2);
        cells.add(at);
        BlockState state = level.getBlockState(new BlockPos(at.x(), at.y(), at.z()));
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            Direction direction = ChestBlock.getConnectedDirection(state);
            cells.add(new BlockCoord(
                    at.x() + direction.getStepX(), at.y() + direction.getStepY(), at.z() + direction.getStepZ()));
        }
        return List.copyOf(cells);
    }

    private static List<BlockCoord> covers(GraphPost post, List<BlockCoord> cells) {
        PlotPlanBlocks.Plan plan = PlotPlanBlocks.of(post.scan.stage());
        List<BlockCoord> covers = new ArrayList<>();
        for (BlockCoord cell : cells) {
            BlockCoord above = new BlockCoord(cell.x(), cell.y() + 1, cell.z());
            BlockPos pos = new BlockPos(above.x(), above.y(), above.z());
            BlockState state = post.level.getBlockState(pos);
            if (state.isAir() || plan.planned(above)) {
                continue;
            }
            if (DemolitionGuard.protectedForDemolish(post.level, above, BlockStates.idOf(state), plan)) {
                continue;
            }
            covers.add(above);
        }
        return List.copyOf(covers);
    }

    private static List<BlockCoord> emptyCells(ServerLevel level, Set<BlockCoord> cells) {
        List<BlockCoord> empty = new ArrayList<>();
        for (BlockCoord cell : cells) {
            if (level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z())).isAir()) {
                empty.add(cell);
            }
        }
        return List.copyOf(empty);
    }

    private static int planks(GraphPost post) {
        return ChestClearance.chestWood(post.stock.available());
    }

    private static long distance(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
