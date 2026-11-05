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
    private WaitRefresh() {}

    static void tick(
            Board board,
            List<SpiritEntity> spirits,
            Map<String, TicketTarget> targets,
            StockView stock,
            CrafterMemory crafters,
            long now) {
        DispatchRound round = new DispatchRound(board, now);
        DispatchPick pick = new DispatchPick(round, spirits, stock);
        for (BoardTicket ticket : DispatchRound.order(board.claimable(stock, now))) {
            SpiritEntity chosen = pick.choose(ticket, targets.get(ticket.id()), crafters);
            board.noteWait(ticket.id(), chosen == null ? pick.why(ticket, targets.get(ticket.id())) : null);
        }
    }
}
