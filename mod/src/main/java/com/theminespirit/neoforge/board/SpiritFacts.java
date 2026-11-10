package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.neoforge.SpiritBrain;
import com.theminespirit.neoforge.SpiritEntity;

final class SpiritFacts {
    private SpiritFacts() {}

    static boolean noPath(BoardTicket ticket, SpiritEntity spirit) {
        return ticket.boardCase() != BoardCase.RESCUE_SPIRIT
                && spirit.brainIfPresent().flatMap(SpiritBrain::failureFact).isPresent();
    }
}
