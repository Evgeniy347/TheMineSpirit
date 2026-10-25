package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.ChestMove;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.SettlementChestStore;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

final class OldChests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MOVES_AT_ONCE = 2;
    private static final int YARD_SLACK = 3;
    private static final Set<BoardStatus> OPEN =
            Set.of(BoardStatus.PENDING, BoardStatus.WAITING_RESOURCES, BoardStatus.ASSIGNED);

    private OldChests() {}

    static void tick(ServerLevel level, BoardService service, StageSite site, long now) {
        SettlementChestStore store = SettlementChestStore.get(level);
        List<BlockCoord> yard = new ArrayList<>();
        List<BlockCoord> old = new ArrayList<>();
        for (BlockCoord chest : store.chests().all()) {
            (inYard(site, chest) ? yard : old).add(chest);
        }
        if (yard.isEmpty() || old.isEmpty()) {
            return;
        }
        int moving = (int) service.board().all().stream()
                .filter(ticket -> ticket.id().startsWith(ChestMove.PREFIX))
                .filter(ticket ->
                        OPEN.contains(service.board().statusOf(ticket.id()).orElse(BoardStatus.DONE)))
                .count();
        for (BlockCoord chest : old) {
            Map<String, Integer> items = contents(level, chest);
            if (items.isEmpty()) {
                store.forget(new BlockPos(chest.x(), chest.y(), chest.z()));
                LOGGER.info("[chests] сундук {} вне двора пуст — снят с учёта склада поселения", chest);
                continue;
            }
            for (Map.Entry<String, Integer> item : items.entrySet()) {
                if (moving >= MOVES_AT_ONCE) {
                    return;
                }
                if (submit(service, chest, yard.get(0), item.getKey(), item.getValue(), now)) {
                    moving++;
                }
            }
        }
    }

    private static boolean submit(
            BoardService service, BlockCoord from, BlockCoord to, String item, int count, long now) {
        String id = ChestMove.id(from, item);
        BoardStatus status = service.board().statusOf(id).orElse(null);
        if (status != null && OPEN.contains(status)) {
            return false;
        }
        if (status == BoardStatus.DONE) {
            service.reopen(id);
        }
        BoardOutcome outcome = service.submit(
                new BoardTicket(
                        id,
                        BoardCase.SUPPLY_SITE,
                        Optional.empty(),
                        BoardRules.priorityOf(BoardCase.SUPPLY_SITE),
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                TicketTarget.at(to).withSupply(item, count).withRelocate(from, to));
        if (outcome != BoardOutcome.ACCEPTED) {
            return false;
        }
        LOGGER.info("[chests] перенос {} ×{} из старого сундука {} в сундук двора {}", item, count, from, to);
        service.journal()
                .recordEntry(BoardLog.note(
                        id,
                        BoardCase.SUPPLY_SITE,
                        now,
                        List.of("item=" + item, "count=" + count, "from=" + from, "to=" + to),
                        null,
                        ConfigStamp.current()));
        return true;
    }

    private static boolean inYard(StageSite site, BlockCoord chest) {
        return site.yardColumn(chest.x(), chest.z())
                && Math.abs(chest.y() - site.anchor().y()) <= YARD_SLACK;
    }

    private static Map<String, Integer> contents(ServerLevel level, BlockCoord chest) {
        Map<String, Integer> items = new LinkedHashMap<>();
        if (ContainerAccess.open(level, new BlockPos(chest.x(), chest.y(), chest.z()))
                instanceof ContainerAccess.Opened opened) {
            Container container = opened.container();
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty()) {
                    items.merge(
                            BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
                }
            }
        }
        return items;
    }
}
