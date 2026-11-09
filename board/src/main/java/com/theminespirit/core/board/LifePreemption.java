package com.theminespirit.core.board;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class LifePreemption {
    private final Map<UUID, List<BoardTicket>> displaced = new LinkedHashMap<>();
    private final Set<UUID> freed = new LinkedHashSet<>();

    boolean freed(UUID spirit) {
        return freed.contains(spirit);
    }

    Optional<BoardTicket> displace(BoardTicket incoming, DispatchRound.Candidate spirit, Board board) {
        return displace(incoming, spirit, board, false);
    }

    Optional<BoardTicket> displace(
            BoardTicket incoming, DispatchRound.Candidate spirit, Board board, boolean vitalIncoming) {
        if (incoming.boardCase() != BoardCase.ACTIVITY_RETREAT && !vitalIncoming) {
            return Optional.empty();
        }
        BoardTicket work = null;
        for (BoardTicket held : board.assignedTo(spirit.id())) {
            if ((!vitalIncoming && held.priority() >= incoming.priority())
                    || !preemptable(held)
                    || VitalGate.satisfies(held.id(), spirit.needs())) {
                continue;
            }
            if (work == null || held.priority() < work.priority()) {
                work = held;
            }
        }
        if (work == null || !board.release(work.id())) {
            return Optional.empty();
        }
        displaced.computeIfAbsent(spirit.id(), ignored -> new ArrayList<>()).add(work);
        freed.add(spirit.id());
        return Optional.of(work);
    }

    List<BoardTicket> drain(UUID spirit) {
        List<BoardTicket> released = displaced.remove(spirit);
        return released == null ? List.of() : List.copyOf(released);
    }

    private boolean preemptable(BoardTicket ticket) {
        return ticket.boardCase() != BoardCase.ACTIVITY_RETREAT
                && ticket.boardCase() != BoardCase.ACTIVITY_DEFEND
                && ticket.boardCase() != BoardCase.ACTIVITY_ESCAPE
                && ticket.boardCase() != BoardCase.RESCUE_SPIRIT;
    }
}
