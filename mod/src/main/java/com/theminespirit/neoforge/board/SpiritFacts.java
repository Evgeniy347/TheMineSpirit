package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.engine.FailureFact;
import com.theminespirit.neoforge.SpiritBrain;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Optional;

final class SpiritFacts {
    private SpiritFacts() {}

    static boolean noPath(BoardTicket ticket, SpiritEntity spirit) {
        return noPath(ticket.boardCase(), spirit.brainIfPresent().flatMap(SpiritBrain::failureFact));
    }

    static boolean noPath(BoardCase boardCase, Optional<FailureFact> fact) {
        return boardCase != BoardCase.RESCUE_SPIRIT
                && fact.filter(value -> value == FailureFact.NO_PATH).isPresent();
    }
}
