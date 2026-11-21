package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class ChestTickets {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ChestTickets() {}

    public static Set<BlockCoord> cells(ServerLevel level) {
        Set<BlockCoord> cells = new LinkedHashSet<>();
        for (BoardTicket ticket : BoardRegistry.get(level).board().tickets()) {
            BlockCoord at = cell(level, ticket);
            if (at != null) {
                cells.addAll(ChestDemolition.chestCells(level, at));
            }
        }
        return cells;
    }

    public static boolean assigned(ServerLevel level, UUID who, BlockCoord cell) {
        for (BoardTicket ticket : BoardRegistry.get(level).board().tickets()) {
            if (ticket.assignee().filter(who::equals).isEmpty()) {
                continue;
            }
            BlockCoord at = cell(level, ticket);
            if (at != null && ChestDemolition.chestCells(level, at).contains(cell)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable BlockCoord cell(ServerLevel level, BoardTicket ticket) {
        if (!ticket.id().startsWith(ChestDemolition.TICKET)) {
            return null;
        }
        if (!BoardRegistry.get(level)
                .board()
                .statusOf(ticket.id())
                .map(BoardStatus::open)
                .orElse(false)) {
            return null;
        }
        String tail = ticket.id().substring(ChestDemolition.TICKET.length());
        int first = tail.indexOf(',');
        int second = first < 0 ? -1 : tail.indexOf(',', first + 1);
        if (first < 0 || second < 0 || tail.indexOf(',', second + 1) >= 0) {
            return null;
        }
        try {
            return new BlockCoord(
                    Integer.parseInt(tail.substring(0, first).trim()),
                    Integer.parseInt(tail.substring(first + 1, second).trim()),
                    Integer.parseInt(tail.substring(second + 1).trim()));
        } catch (NumberFormatException broken) {
            LOGGER.warn("[warehouse] у задания {} не разобраны координаты: {}", ticket.id(), broken.getMessage());
            return null;
        }
    }
}
