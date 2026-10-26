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
import com.theminespirit.core.inventory.WarehouseSpace;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.SettlementChestStore;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;

final class ChestSpaceWatch {
    static final String PREFIX = "chests:";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<BoardStatus> OPEN =
            EnumSet.of(BoardStatus.PENDING, BoardStatus.WAITING_RESOURCES, BoardStatus.ASSIGNED);

    private ChestSpaceWatch() {}

    static void tick(ServerLevel level, BoardService service, StageSite site, long now) {
        if (!service.board().ledger().has(BoardCase.STOCK_CHESTS, 1) || open(service)) {
            return;
        }
        int free = 0;
        int slots = 0;
        for (BlockCoord chest : SettlementChestStore.get(level).chests().all()) {
            if (ContainerAccess.open(level, new BlockPos(chest.x(), chest.y(), chest.z()))
                    instanceof ContainerAccess.Opened opened) {
                Container container = opened.container();
                slots += container.getContainerSize();
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    free += container.getItem(slot).isEmpty() ? 1 : 0;
                }
            }
        }
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
        BoardOutcome outcome = service.submit(
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

    private static Optional<Integer> nextSlot(ServerLevel level, StageSite site) {
        List<List<BlockCoord>> slots = site.chestSlots();
        for (int index = 0; index < slots.size(); index++) {
            boolean taken = slots.get(index).stream()
                    .anyMatch(cell -> level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z()))
                            .is(Blocks.CHEST));
            if (!taken) {
                return Optional.of(index + 1);
            }
        }
        return Optional.empty();
    }

    private static boolean open(BoardService service) {
        return service.board().all().stream()
                .filter(ticket -> ticket.id().startsWith(PREFIX))
                .anyMatch(ticket ->
                        OPEN.contains(service.board().statusOf(ticket.id()).orElse(BoardStatus.DONE)));
    }
}
