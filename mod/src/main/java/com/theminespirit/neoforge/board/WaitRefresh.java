package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;

final class WaitRefresh {
    static final int BATCH = 40;

    private static int cursor;

    private WaitRefresh() {}

    static void tick(
            Board board,
            List<SpiritEntity> spirits,
            Map<String, TicketTarget> targets,
            StockView stock,
            CrafterMemory crafters,
            long now) {
        List<BoardTicket> order = DispatchRound.order(board.claimable(stock, now));
        if (order.isEmpty()) {
            return;
        }
        DispatchRound round = new DispatchRound(board, now);
        DispatchPick pick = new DispatchPick(round, spirits, stock);
        int start = cursor % order.size();
        for (int index : window(start, order.size(), BATCH)) {
            BoardTicket ticket = order.get(index);
            SpiritEntity chosen = pick.choose(ticket, targets.get(ticket.id()), crafters);
            board.noteWait(ticket.id(), chosen == null ? pick.why(ticket, targets.get(ticket.id())) : null);
        }
        cursor = start + Math.min(BATCH, order.size());
    }

    static int[] window(int start, int size, int batch) {
        int count = Math.min(batch, size);
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = (start + i) % size;
        }
        return out;
    }
}
