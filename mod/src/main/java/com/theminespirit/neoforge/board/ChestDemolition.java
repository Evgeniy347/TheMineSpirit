package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLedger;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.CaseMaterials;
import com.theminespirit.core.board.ChestClearance;
import com.theminespirit.core.board.ChestPlans;
import com.theminespirit.core.board.StateChangeLog;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.neoforge.SettlementChestStore;
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

final class ChestDemolition {
    static final String EVENT = "demolish_chest";
    private static final String TICKET = "demolish_chest:";
    private static final String CHEST = "warehouse_chest:";
    private static final String PLANKS = "_planks";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final StateChangeLog CHANGES = new StateChangeLog();

    private ChestDemolition() {}

    static boolean pending(ServerLevel level, BoardService service) {
        BoardLedger ledger = service.board().ledger();
        Set<BlockCoord> cells = WarehouseLocations.cells(level, ledger);
        if (cells.isEmpty()) {
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
        Set<BlockCoord> cells = WarehouseLocations.cells(post.level, ledger);
        if (cells.isEmpty()) {
            return true;
        }
        if (busy(post)) {
            return true;
        }
        List<BlockCoord> extras = new ArrayList<>(WarehouseLocations.extras(post.level, ledger));
        extras.sort(
                (first, second) -> Long.compare(distance(post.scan.home(), first), distance(post.scan.home(), second)));
        List<ContainerSnapshot> warehouse = ChestSnapshots.read(post.level, List.copyOf(cells));
        List<BlockCoord> emptyCells = emptyCells(post.level, cells);
        int planks = planks(post);
        for (BlockCoord at : extras) {
            Optional<ContainerSnapshot> snapshot = Optional.ofNullable(ChestSnapshots.snapshot(post.level, at));
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
                    submit(post, ticket, at, ChestPlans.breakOnly(chestCells(post.level, at)), chest, decision);
                    return true;
                }
                case DRAIN -> {
                    submit(post, ticket, at, ChestPlans.demolish(chest, chestCells(post.level, at)), chest, decision);
                    return true;
                }
                case ADD_CHEST -> {
                    placeChest(post, ticket, emptyCells.get(0));
                    return true;
                }
                case WAIT -> {
                    if (CHANGES.changed("chest-wait:" + at, decision.reason())) {
                        LOGGER.info("[warehouse] сундук {} ждёт: {}", at, decision.reason());
                    }
                }
            }
        }
        return true;
    }

    private static void submit(
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
        post.submit(
                post.ticket(
                        id,
                        BoardCase.DISMANTLE_SHELTER,
                        Optional.empty(),
                        BoardRules.priorityOf(BoardCase.DISMANTLE_SHELTER),
                        ticket.id(),
                        Map.of()),
                TicketTarget.at(at).withPlan(plan).withWork(work));
        LOGGER.info(
                "[warehouse] сундук {}: {} (вещей {}, слотов нужно {} из {})",
                at,
                decision.reason(),
                ChestClearance.moves(chest).size(),
                decision.neededSlots(),
                decision.freeSlots());
    }

    private static void placeChest(GraphPost post, GraphTickets.Ticket ticket, BlockCoord cell) {
        TaskNode plan = ChestPlans.placeOne(cell, post.scan.wood());
        Map<String, Integer> required = CaseMaterials.need(plan, FirstStepsRunner.materialSource(post.level));
        String id = CHEST + cell.x() + "," + cell.y() + "," + cell.z();
        post.submit(
                post.ticket(
                        id,
                        BoardCase.STOCK_CHESTS,
                        Optional.empty(),
                        BoardRules.priorityOf(BoardCase.STOCK_CHESTS),
                        ticket.id(),
                        required),
                TicketTarget.at(cell).withPlan(plan));
        LOGGER.info("[warehouse] склад полон: новый сундук ставится в клетку склада {}", cell);
    }

    private static boolean busy(GraphPost post) {
        return post.service.board().tickets().stream()
                .filter(ticket -> ticket.id().startsWith(TICKET) || ticket.id().startsWith(CHEST))
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
        int planks = 0;
        for (Map.Entry<String, Integer> entry : post.stock.available().entrySet()) {
            if (entry.getKey().endsWith(PLANKS)) {
                planks += entry.getValue();
            }
        }
        return planks;
    }

    private static long distance(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
