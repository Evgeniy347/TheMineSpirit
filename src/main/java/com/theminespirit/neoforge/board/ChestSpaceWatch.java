package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.ChestSpace;
import com.theminespirit.core.town.SettlementReach;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.YardScheme;
import com.theminespirit.neoforge.terminal.CraftTerminalService;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;

final class ChestSpaceWatch {
    static final String PREFIX = "chests:";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<BoardStatus> OPEN =
            EnumSet.of(BoardStatus.PENDING, BoardStatus.WAITING_RESOURCES, BoardStatus.ASSIGNED);

    private ChestSpaceWatch() {}

    static boolean post(GraphPost post) {
        ServerLevel level = post.level;
        BoardService service = post.service;
        StageSite site = post.scan.stage();
        long now = post.now;
        if (open(service)) {
            return true;
        }
        Optional<Integer> next = nextSlot(level, site);
        if (next.isEmpty()) {
            LOGGER.info("[chests] ряд сундуков двора занят — нужен склад этапа 2: узел store:space ждёт места");
            return true;
        }
        int slot = next.orElseThrow();
        String id = PREFIX + slot;
        if (submit(post, level, site, slot) != BoardOutcome.ACCEPTED) {
            return true;
        }
        Space space = space(level);
        int threshold = DataTables.INSTANCE.town().current().spacing().chestFreePercent();
        LOGGER.info(
                "[chests] {}: свободно {} из {} слотов (< {}%) — двойной сундук в место {} двора (узел store:space)",
                id, space.free(), space.slots(), threshold, slot);
        service.journal()
                .recordEntry(BoardLog.note(
                        id,
                        BoardCase.STOCK_CHESTS,
                        now,
                        List.of(
                                "free=" + space.free(),
                                "slots=" + space.slots(),
                                "threshold=" + threshold,
                                "slot=" + slot),
                        null,
                        ConfigStamp.current()));
        return true;
    }

    private static BoardOutcome submit(GraphPost post, ServerLevel level, StageSite site, int slot) {
        String id = PREFIX + slot;
        return post.submit(
                post.ticket(
                        id,
                        BoardCase.STOCK_CHESTS,
                        Optional.empty(),
                        BoardRules.priorityOf(BoardCase.STOCK_CHESTS),
                        BoardTicket.ALWAYS_CAUSE,
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
        for (ContainerSnapshot snapshot : StockReader.chests(level)) {
            BlockCoord chest = snapshot.pos();
            spots.add(new ChestSpace.Spot(chest, snapshot.freeSlots(), snapshot.slotCount()));
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
