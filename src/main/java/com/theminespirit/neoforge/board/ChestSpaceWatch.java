package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.board.StateChangeLog;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.inventory.WarehouseSpace;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.ChestSpace;
import com.theminespirit.core.town.SettlementReach;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.YardScheme;
import com.theminespirit.neoforge.SettlementChestStore;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import com.theminespirit.neoforge.terminal.CraftTerminalService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.slf4j.Logger;

final class ChestSpaceWatch {
    static final String PREFIX = "chests:";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final StateChangeLog CHANGES = new StateChangeLog();
    private static final Set<BoardStatus> OPEN =
            EnumSet.of(BoardStatus.PENDING, BoardStatus.WAITING_RESOURCES, BoardStatus.ASSIGNED);

    private ChestSpaceWatch() {}

    static void tick(ServerLevel level, BoardService service, StageSite site, long now) {
        if (!service.board().ledger().has(BoardCase.STOCK_CHESTS, 1) || open(service)) {
            return;
        }
        Space space = space(level);
        int free = space.free();
        int slots = space.slots();
        int threshold = DataTables.INSTANCE.town().current().spacing().chestFreePercent();
        if (!WarehouseSpace.needsChest(free, slots, threshold)) {
            return;
        }
        Optional<Integer> next = nextSlot(level, site);
        if (next.isEmpty()) {
            LOGGER.info(
                    "[chests] свободно {} из {} слотов (< {}%), а ряд сундуков двора занят — нужен склад этапа 2",
                    free, slots, threshold);
            return;
        }
        int slot = next.orElseThrow();
        String id = PREFIX + slot;
        BoardOutcome outcome = submit(level, service, site, slot, now);
        if (outcome != BoardOutcome.ACCEPTED) {
            return;
        }
        LOGGER.info(
                "[chests] {}: свободно {} из {} слотов (< {}%) — двойной сундук в место {} двора",
                id, free, slots, threshold, slot);
        service.journal()
                .recordEntry(BoardLog.note(
                        id,
                        BoardCase.STOCK_CHESTS,
                        now,
                        List.of("free=" + free, "slots=" + slots, "threshold=" + threshold, "slot=" + slot),
                        null,
                        ConfigStamp.current()));
    }

    static void request(ServerLevel level, BoardService service, StageSite site, long now, String cause) {
        if (open(service)) {
            return;
        }
        Optional<Integer> next = nextSlot(level, site);
        if (next.isEmpty()) {
            if (CHANGES.changed("chests:request", "full")) {
                LOGGER.info("[chests] нужен сундук ({}): ряд сундуков двора занят — нужен склад этапа 2", cause);
            }
            return;
        }
        int slot = next.orElseThrow();
        if (submit(level, service, site, slot, now) != BoardOutcome.ACCEPTED) {
            return;
        }
        if (CHANGES.changed("chests:request", "slot:" + slot)) {
            LOGGER.info("[chests] заявка на сундук ({}): место {} двора", cause, slot);
        }
    }

    private static BoardOutcome submit(ServerLevel level, BoardService service, StageSite site, int slot, long now) {
        String id = PREFIX + slot;
        return service.submit(
                new BoardTicket(
                        id,
                        BoardCase.STOCK_CHESTS,
                        Optional.empty(),
                        BoardRules.priorityOf(BoardCase.STOCK_CHESTS),
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                TicketTarget.at(site.workYard())
                        .withWork(StageBuilds.of(level, BoardCase.STOCK_CHESTS, site, slot))
                        .withCount(slot));
    }

    record Space(int free, int slots) {
        int freePercent() {
            return slots == 0 ? 100 : free * 100 / slots;
        }
    }

    static Space space(ServerLevel level) {
        List<ChestSpace.Spot> spots = new ArrayList<>();
        Set<Container> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BlockCoord chest : SettlementChestStore.get(level).chests().all()) {
            BlockPos at = new BlockPos(chest.x(), chest.y(), chest.z());
            var state = level.getBlockState(at);
            if (state.is(Blocks.CHEST) && state.getValue(ChestBlock.TYPE) == ChestType.RIGHT) {
                continue;
            }
            if (ContainerAccess.open(level, at) instanceof ContainerAccess.Opened opened) {
                Container container = opened.container();
                if (!seen.add(container)) {
                    continue;
                }
                int free = 0;
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    free += container.getItem(slot).isEmpty() ? 1 : 0;
                }
                spots.add(new ChestSpace.Spot(chest, free, container.getContainerSize()));
            }
        }
        SettlementReach reach = CraftTerminalService.reach(level).orElse(null);
        ChestSpace space = ChestSpace.of(spots, reach);
        return new Space(space.free(), space.slots());
    }

    private static Optional<Integer> nextSlot(ServerLevel level, StageSite site) {
        List<List<BlockCoord>> slots = YardScheme.slots(site);
        for (int index = 0; index < slots.size(); index++) {
            if (!occupied(level, slots.get(index))) {
                return Optional.of(index + 1);
            }
        }
        List<List<BlockCoord>> overflow = YardScheme.overflowSlots(site);
        for (int index = 0; index < overflow.size(); index++) {
            List<BlockCoord> pair = overflow.get(index);
            if (pair.stream()
                    .allMatch(cell -> level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z()))
                            .isAir())) {
                return Optional.of(slots.size() + index + 1);
            }
        }
        return Optional.empty();
    }

    private static boolean occupied(ServerLevel level, List<BlockCoord> pair) {
        return pair.stream()
                .anyMatch(cell -> level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z()))
                        .is(Blocks.CHEST));
    }

    private static boolean open(BoardService service) {
        return service.board().all().stream()
                .filter(ticket -> ticket.id().startsWith(PREFIX))
                .anyMatch(ticket ->
                        OPEN.contains(service.board().statusOf(ticket.id()).orElse(BoardStatus.DONE)));
    }
}
