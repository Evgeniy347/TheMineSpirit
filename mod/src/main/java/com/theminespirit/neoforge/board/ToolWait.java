package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.ToolSet;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import org.slf4j.Logger;

final class ToolWait {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ToolWait() {}

    static void release(Board board, Map<String, Long> issued, SpiritEntity spirit, BoardTicket ticket) {
        issued.remove(ticket.id());
        board.release(ticket.id());
        LOGGER.warn(
                "[tool] дух {}: задание {} не закрыто — {} у духа нет, задание возвращено доске и ждёт инструмент",
                spirit.displayName(),
                ticket.id(),
                ToolSet.itemId(ticket.boardCase()));
    }
}
